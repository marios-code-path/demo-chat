package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import io.rsocket.exceptions.RejectedSetupException
import io.rsocket.metadata.WellKnownMimeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.util.MimeTypeUtils
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/** The Agent account of `userinit.yml`. It holds no wildcard row. */
private const val AGENT = "Agent"

/**
 * The Agent password for this test.
 *
 * `userinit.yml` leaves that password blank, so a start generates one and
 * prints it. A test cannot read a generated value, so the property below
 * supplies it instead. The binder merges it into the `Agent` entry that the
 * file declares.
 */
private const val AGENT_SECRET = "credentialseamsecret"

/**
 * The credential seam of the deployed RSocket server.
 *
 * `RSocketServerConfiguration` builds its security with
 * `simpleAuthentication` and `anonymous`. The manager behind
 * `simpleAuthentication` is the one Spring Security builds from the
 * `ReactiveUserDetailsService` bean, because this deployment declares no
 * `ReactiveAuthenticationManager` bean of its own. That service is
 * `CoreUserDetailsService`, and it reads the credential from the secrets
 * store.
 *
 * **Every assertion here reads a running composition root.** Nothing mocks the
 * manager, the user service or the secrets store, so the whole path from a
 * setup frame to an access decision is measured.
 *
 * `CompositeAccessEnforcementTests` pins the other half: a caller that
 * presents no credential creates a room and is then refused a removal of that
 * same room. So the removal below turns on the identity the credential
 * supplies, and on nothing else.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-credential-seam",
        "app.server.proto=rsocket", "server.port=0", "spring.rsocket.server.port=0",
        "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory", "app.service.core.pubsub=memory",
        "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory",
        "app.service.composite", "app.service.composite.auth",
        "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic",
        "app.controller.pubsub",
        "app.service.security.userdetails", "app.users.create=true",
        "app.init.initial-users.Agent.password=$AGENT_SECRET"
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RSocketCredentialSeamTests {

    @Autowired lateinit var builder: RSocketRequester.Builder
    @Autowired lateinit var stores: PersistenceServiceBeans<Long, String>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val timeout = Duration.ofSeconds(10)

    private val mapper = JsonMapper.builder()
        .addModule(ChatJackson3Modules().chatJackson3Module())
        .build()

    /**
     * **A credential that the secrets store does not hold refuses the setup
     * frame.** The server answers `RejectedSetupException` (error code `0x3`)
     * and terminates the connection.
     *
     * **The refusal does not arrive on the connect call.** `connectTcp`
     * completes with a requester, and the rejection lands on the first request
     * that uses it. A client that only awaits its connect call reads a refused
     * setup as a success.
     *
     * The message text belongs to
     * `AbstractUserDetailsReactiveAuthenticationManager`, which is the manager
     * Spring Security builds here.
     *
     * This test is one of the two that fail when `simpleAuthentication` leaves
     * the security builder.
     */
    @Test
    fun `an invalid credential is refused at the setup seam`() {
        val requester = connect(AGENT, "not-the-password").block(timeout)!!

        StepVerifier.create(
            requester.route("topic.topic-add")
                .data(ByStringRequest("badcredroom"))
                .retrieveMono(Key::class.java)
        ).expectErrorSatisfies { error ->
            assertThat(error)
                .describedAs("the refusal of a wrong credential")
                .isInstanceOf(RejectedSetupException::class.java)
                .hasMessageContaining("Invalid Credentials")
        }.verify(timeout)
    }

    /**
     * **A valid credential authenticates the caller, and that caller owns what
     * it creates.**
     *
     * The Agent account holds no wildcard row. The shipped rows grant
     * `MessageTopic:NEW` to every caller that holds an identity, so the add
     * allows for anyone. The removal carries `REM`, and only the owner row of
     * that one room grants it. So the removal allows here exactly because the
     * credential supplied the Agent identity, and the room owner writer
     * recorded it.
     */
    @Test
    fun `a valid credential supplies the identity that owns the created room`() {
        val requester = connect(AGENT, AGENT_SECRET).block(timeout)!!

        val room = createRoom(requester, "credentialseamroom")

        StepVerifier.create(
            requester.route("topic.topic-rem")
                .data(ByIdRequest(room.id))
                .retrieveMono(Void::class.java)
        ).expectComplete().verify(timeout)

        assertThat(stores.topicPersistence().get(room).block(timeout))
            .describedAs("the room after its owner removed it")
            .isNull()
    }

    private fun connect(username: String, password: String): Mono<RSocketRequester> =
        builder
            .rsocketStrategies { strategies ->
                strategies
                    .decoders { it.add(0, JacksonJsonDecoder(mapper)) }
                    .encoder(SimpleAuthenticationEncoder())
            }
            .setupMetadata(
                UsernamePasswordMetadata(username, password),
                MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string),
            )
            .connectTcp("localhost", port)

    @Suppress("UNCHECKED_CAST")
    private fun createRoom(requester: RSocketRequester, name: String): Key<Long> =
        requester.route("topic.topic-add")
            .data(ByStringRequest(name))
            .retrieveMono(Key::class.java)
            .block(timeout)!! as Key<Long>
}
