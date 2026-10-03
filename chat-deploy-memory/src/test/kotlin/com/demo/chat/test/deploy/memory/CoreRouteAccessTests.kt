package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.StringRoleAuthorizationMetadata
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.security.AuthenticationService
import io.rsocket.metadata.WellKnownMimeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.util.MimeTypeUtils
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/** The service account of this test. `app.security.service-accounts` names it. */
private const val SERVICE = "Agent"
private const val SERVICE_SECRET = "coreroutesservicesecret"

/** The shipped Admin password of `userinit.yml`. */
private const val ADMIN_SECRET = "changeme"

/** A plain user that this test creates. */
private const val PLAIN = "coreroutesplain"
private const val PLAIN_SECRET = "coreroutesplainsecret"

/**
 * The core RSocket routes refuse every caller that holds neither
 * `ROLE_SERVICE` nor `ROLE_ADMIN`.
 *
 * **Measured before the rule, on 2026-10-02.** A caller with no credential read
 * the Admin password hash on `secrets.get`, read every grant on
 * `persist.authmetadata.all`, wrote a `*` grant on `persist.authmetadata.add`,
 * and sent a message in the name of Admin on `pubsub.sendMessage`. Each call
 * completed. See `CHAT-rdlghoqe`.
 *
 * Every assertion reads a running composition root over TCP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-core-route-access",
        "app.server.proto=rsocket", "server.port=0", "spring.rsocket.server.port=0",
        "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory", "app.service.core.pubsub=memory",
        "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory",
        "app.service.composite", "app.service.composite.auth=true",
        "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic",
        "app.controller.pubsub", "app.controller.secrets",
        "app.service.security.userdetails", "app.users.create=true",
        "app.init.initial-users.Agent.password=$SERVICE_SECRET",
        "app.security.service-accounts=$SERVICE",
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoreRouteAccessTests {

    @Autowired lateinit var strategies: RSocketStrategies
    @Autowired lateinit var stores: PersistenceServiceBeans<Long, String>
    @Autowired lateinit var composite: CompositeServiceBeans<Long, String>
    @Autowired lateinit var rootKeys: RootKeys<Long>
    @Autowired lateinit var authentication: AuthenticationService<Long>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val timeout = Duration.ofSeconds(10)

    private val mapper = JsonMapper.builder()
        .addModule(ChatJackson3Modules().chatJackson3Module())
        .build()

    private lateinit var room: Key<Long>

    @BeforeAll
    fun fixtures() {
        room = composite.topicService().addRoom(ByStringRequest("coreroutesroom")).block(timeout)!!
        val plain = composite.userService()
            .addUser(UserCreateRequest("name-$PLAIN", PLAIN, "http://u")).block(timeout)!!
        // The service stores the value as given, so it carries the encoder id.
        authentication.setAuthentication(plain, "{noop}$PLAIN_SECRET").block(timeout)
    }

    /** **Each of the four measured calls is refused now, and no row is written.** */
    @Test
    fun `a caller with no credential is refused every core write and read`() {
        val requester = connect(null)
        val row = grantRow()

        assertRefused(requester.route("secrets.get").data(rootKeys.admin()).retrieveMono(String::class.java))
        assertRefused(requester.route("persist.authmetadata.all").retrieveFlux(Map::class.java).collectList())
        assertRefused(requester.route("persist.authmetadata.add").data(row).retrieveMono(Void::class.java))
        assertRefused(requester.route("pubsub.sendMessage").data(forgedMessage()).retrieveMono(Void::class.java))
        assertRefused(requester.route("key.key").data(ChatDomain.MESSAGE).retrieveMono(Key::class.java))

        assertThat(stores.authMetaPersistence().get(row.key).block(timeout))
            .describedAs("the grant row after a refused write")
            .isNull()
    }

    /** **A plain user is refused too.** A credential alone does not open the core routes. */
    @Test
    fun `a plain user is refused the core routes`() {
        val requester = connect(UsernamePasswordMetadata(PLAIN, PLAIN_SECRET))

        assertRefused(requester.route("secrets.get").data(rootKeys.admin()).retrieveMono(String::class.java))
        assertRefused(requester.route("persist.authmetadata.all").retrieveFlux(Map::class.java).collectList())
    }

    /**
     * **The registry reads stay open.** The shell resolves a room creator
     * through `key.rootOf` before `addRoom`, and an anonymous caller may add a
     * room. Both registry reads stay open, so the test reads both.
     */
    @Test
    fun `a caller with no credential still reads the key registry`() {
        val requester = connect(null)

        val root = requester.route("key.rootOf").data(room.id).retrieveMono(Long::class.java).block(timeout)
        val exists = requester.route("key.exists").data(room).retrieveMono(Boolean::class.java).block(timeout)

        assertThat(root).describedAs("the root of the room").isEqualTo(room.root)
        assertThat(exists).describedAs("the room key exists").isTrue()
    }

    @Test
    fun `the service account reaches the core routes`() {
        val requester = connect(UsernamePasswordMetadata(SERVICE, SERVICE_SECRET))

        val rows = requester.route("persist.authmetadata.all").retrieveFlux(Map::class.java)
            .collectList().block(timeout)!!

        assertThat(rows).describedAs("the grant rows").isNotEmpty
    }

    /**
     * **A service sends its credential on each request, not at setup.**
     * `MetadataRSocketRequester` adds the metadata that
     * `ServiceCredential.metadataProvider` builds to every route call. This
     * connection carries no setup credential, so the request metadata alone
     * decides.
     */
    @Test
    fun `the service credential in request metadata reaches the core routes`() {
        val rows = connect(null).route("persist.authmetadata.all")
            .metadata(
                UsernamePasswordMetadata(SERVICE, SERVICE_SECRET),
                MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string),
            )
            .retrieveFlux(Map::class.java)
            .collectList().block(timeout)!!

        assertThat(rows).describedAs("the grant rows").isNotEmpty
    }

    @Test
    fun `the Admin account reaches the core routes`() {
        val requester = connect(UsernamePasswordMetadata("Admin", ADMIN_SECRET))

        val rows = requester.route("persist.authmetadata.all").retrieveFlux(Map::class.java)
            .collectList().block(timeout)!!

        assertThat(rows).describedAs("the grant rows").isNotEmpty
    }

    /** The row the probe wrote: every caller would own every room. */
    private fun grantRow() = StringRoleAuthorizationMetadata(
        stores.authMetaPersistence().key().block(timeout)!!,
        rootKeys.anon(),
        rootKeys.of(ChatDomain.MESSAGE_TOPIC),
        "*",
        false,
        0L,
    )

    /** A message in the name of Admin, to a room that nobody joined. */
    private fun forgedMessage(): Message<Long, String> {
        val key = stores.messagePersistence().key().block(timeout)!!
        return Message.create(MessageKey.of(key.id, key.root, rootKeys.admin().id, room.id), "forged", true)
    }

    /** RSocket answers a refused request with a typed authorization envelope. */
    private fun assertRefused(call: Mono<*>) {
        val error = call.then(Mono.empty<Throwable>()).onErrorResume { Mono.just(it) }.block(timeout)

        assertThat(error).describedAs("the refusal").isNotNull
        assertThat(error!!.message)
            .describedAs("the refusal envelope")
            .contains("\"kind\":\"AUTHORIZATION\"")
    }

    /**
     * **A fresh builder per connection.** The injected builder is one mutable
     * object, so a `setupMetadata` call on it reaches every later connection.
     */
    private fun connect(credential: UsernamePasswordMetadata?): RSocketRequester {
        val configured = RSocketRequester.builder().rsocketStrategies(
            strategies.mutate()
                .decoders { it.add(0, JacksonJsonDecoder(mapper)) }
                .encoders { it.add(0, JacksonJsonEncoder(mapper)) }
                .encoder(SimpleAuthenticationEncoder())
                .build()
        )
        val withCredential = credential?.let {
            configured.setupMetadata(
                it, MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string)
            )
        } ?: configured
        return withCredential.connectTcp("localhost", port).block(timeout)!!
    }
}
