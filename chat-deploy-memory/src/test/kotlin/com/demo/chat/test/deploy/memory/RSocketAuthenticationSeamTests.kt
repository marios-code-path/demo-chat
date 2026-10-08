package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.security.rsocket.RSocketSecurityErrorCodes
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.rsocket.exceptions.CustomRSocketException
import io.rsocket.exceptions.RejectedSetupException
import io.rsocket.metadata.AuthMetadataCodec
import io.rsocket.metadata.WellKnownMimeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.rsocket.metadata.BearerTokenAuthenticationEncoder
import org.springframework.security.rsocket.metadata.BearerTokenMetadata
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.util.MimeTypeUtils
import reactor.test.StepVerifier
import java.nio.charset.StandardCharsets
import java.time.Duration

private const val AGENT = "Agent"

/** A credential that the requester sends in the setup frame, not per request. */
private class SetupCredential(val metadata: Any)

/** Any bytes under the legacy `basic` authentication MIME type. */
private object LegacyBasicCredential

private const val AGENT_CLIENT = "seam-client"
private const val AGENT_SECRET = "authenticationseamsecret"

/**
 * The authentication policy of the deployed RSocket seam. See `CHAT-jkordfef`
 * and `docs/IDENTITY-POLICY.md`.
 *
 * Each test sends one credential case to a running server, and then reads the
 * result from the stores. Nothing mocks the managers, the user service, the
 * secrets store, or the JWT decoder.
 *
 * Three results are possible, and each test names the one it expects:
 *
 * - **A refusal.** The server answers `0x401`, or `RejectedSetupException` at
 *   setup. The room is not created. Every credential that the seam cannot
 *   accept takes this result, an unsupported one included.
 * - **The `Anon` identity.** The room is created, and no owner row exists,
 *   because `ContextRoomOwnerGrant` writes no row for `Anon`. A core route
 *   answers `0x403`, which is an authorization refusal and not an
 *   authentication refusal.
 * - **A user identity.** The room is created, and its owner row names the key
 *   of that user.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-rsocket-authentication-seam",
        "app.server.proto=rsocket", "server.port=0", "spring.rsocket.server.port=0",
        "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory", "app.service.core.pubsub=memory",
        "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite=true",
        "app.service.composite.auth=true",
        "app.controller.key=true", "app.controller.persistence=true", "app.controller.index=true",
        "app.controller.pubsub=true", "app.controller.secrets=true", "app.controller.user=true",
        "app.controller.topic=true", "app.controller.message=true",
        "app.service.security.userdetails=true",
        "app.users.create=true",
        "app.init.initial-users.Agent.password=$AGENT_SECRET",
        "app.security.required-scope=chat.mcp",
        "app.security.agents[0].client-id=$AGENT_CLIENT",
        "app.security.agents[0].username=$AGENT",
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RSocketAuthenticationSeamTests {

    @Autowired lateinit var strategies: RSocketStrategies
    @Autowired lateinit var composite: CompositeServiceBeans<Long, String>
    @Autowired lateinit var stores: PersistenceServiceBeans<Long, String>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val timeout = Duration.ofSeconds(10)

    @Test
    fun `no credential takes the Anon identity`() {
        val requester = requester()
        try {
            StepVerifier.create(addRoom(requester, "seamnocredential"))
                .expectNextCount(1).verifyComplete()

            StepVerifier.create(requester.route("persist.user.all").retrieveFlux(Map::class.java))
                .expectErrorSatisfies { assertCode(it, RSocketSecurityErrorCodes.AUTHORIZATION) }
                .verify(timeout)
        } finally {
            requester.dispose()
        }

        assertThat(ownersOf("seamnocredential")).describedAs("the owner rows of an Anon room").isEmpty()
    }

    @Test
    fun `a valid password at setup takes the identity of its user`() {
        val requester = requester(SetupCredential(UsernamePasswordMetadata(AGENT, AGENT_SECRET)))
        try {
            StepVerifier.create(addRoom(requester, "seamvalidpassword"))
                .expectNextCount(1).verifyComplete()
        } finally {
            requester.dispose()
        }

        assertThat(ownersOf("seamvalidpassword")).containsExactly(keyOf(AGENT))
    }

    @Test
    fun `a valid bearer takes the identity of its agent`() {
        val bearer = BearerTokenMetadata(AgentTestTokens.mint(signingKeyPath, AGENT_CLIENT))
        val requester = requester(bearer)
        try {
            StepVerifier.create(
                addRoom(requester, "seamvalidbearer", bearer)
            ).expectNextCount(1).verifyComplete()
        } finally {
            requester.dispose()
        }

        assertThat(ownersOf("seamvalidbearer")).containsExactly(keyOf(AGENT))
    }

    /**
     * The refusal does not arrive on the connect call. It lands on the first
     * request. See `RSocketServiceCredentialPreservedTests`.
     */
    @Test
    fun `an invalid password at setup is refused`() {
        val requester = requester(SetupCredential(UsernamePasswordMetadata(AGENT, "not-the-password")))
        try {
            StepVerifier.create(addRoom(requester, "seambadsetup"))
                .expectErrorSatisfies { error ->
                    assertThat(error)
                        .isInstanceOf(RejectedSetupException::class.java)
                        .hasMessage("Invalid Credentials")
                }.verify(timeout)
        } finally {
            requester.dispose()
        }

        assertThat(roomExists("seambadsetup")).isFalse()
    }

    @Test
    fun `an invalid password in request metadata is refused`() {
        assertRefused("seambadrequestpassword", UsernamePasswordMetadata(AGENT, "not-the-password"))
    }

    @Test
    fun `a bearer signed by another key is refused`() {
        assertRefused("seamforgedbearer", BearerTokenMetadata(AgentTestTokens.mint(foreignKeyPath, AGENT_CLIENT)))
    }

    @Test
    fun `a bearer that is not a token is refused`() {
        assertRefused("seamjunkbearer", BearerTokenMetadata("not-a-token"))
    }

    @Test
    fun `an expired bearer is refused as an invalid bearer is`() {
        assertRefused(
            "seamexpiredbearer",
            BearerTokenMetadata(AgentTestTokens.mint(signingKeyPath, AGENT_CLIENT, expiresInMillis = -600_000)),
        )
    }

    /**
     * An authentication metadata entry with an auth type that is not well
     * known. `AuthenticationPayloadExchangeConverter` answers no
     * authentication for it, so before `UnsupportedCredentialRefusal` the
     * caller took `Anon` and the room was created.
     */
    @Test
    fun `an unsupported credential type is refused`() {
        assertRefused("seamunsupported", unsupportedCredential())
    }

    /** No converter here reads the legacy `basic` MIME type. */
    @Test
    fun `a legacy basic credential is refused`() {
        assertRefused("seamlegacybasic", LegacyBasicCredential)
    }

    @Test
    fun `an unsupported credential type at setup is refused`() {
        val requester = requester(SetupCredential(unsupportedCredential()))
        try {
            StepVerifier.create(addRoom(requester, "seamunsupportedsetup"))
                .expectErrorSatisfies { error ->
                    assertThat(error).isInstanceOf(RejectedSetupException::class.java)
                }.verify(timeout)
        } finally {
            requester.dispose()
        }

        assertThat(roomExists("seamunsupportedsetup")).isFalse()
    }

    private fun assertRefused(room: String, credential: Any) {
        val requester = requester(credential)
        try {
            StepVerifier.create(addRoom(requester, room, credential))
                .expectErrorSatisfies { assertCode(it, RSocketSecurityErrorCodes.AUTHENTICATION) }
                .verify(timeout)
        } finally {
            requester.dispose()
        }

        assertThat(roomExists(room)).describedAs("the room after a refused credential").isFalse()
    }

    private fun assertCode(error: Throwable, code: Int) {
        assertThat(error).isInstanceOf(CustomRSocketException::class.java)
        assertThat((error as CustomRSocketException).errorCode()).isEqualTo(code)
    }

    private fun addRoom(requester: RSocketRequester, name: String, credential: Any? = null) =
        requester.route("topic.topic-add")
            .let { spec ->
                when (credential) {
                    null -> spec
                    LegacyBasicCredential -> spec.metadata("user:password".toByteArray(), LEGACY_BASIC)
                    else -> spec.metadata(credential, AUTHENTICATION)
                }
            }
            .data(ByStringRequest(name))
            .retrieveMono(Map::class.java)

    /**
     * A requester whose strategies encode one credential type.
     *
     * Both security encoders claim the authentication MIME type, so the first
     * registered one takes every value. Register only the one that matches.
     */
    private fun requester(credential: Any? = null): RSocketRequester = RSocketRequester.builder()
        .rsocketStrategies(
            strategies.mutate().encoders { encoders ->
                when (credential) {
                    is UsernamePasswordMetadata -> encoders.add(0, SimpleAuthenticationEncoder())
                    is SetupCredential -> if (credential.metadata is UsernamePasswordMetadata) {
                        encoders.add(0, SimpleAuthenticationEncoder())
                    }
                    is BearerTokenMetadata -> encoders.add(0, BearerTokenAuthenticationEncoder())
                }
            }.build()
        )
        .let { builder ->
            (credential as? SetupCredential)?.let { builder.setupMetadata(it.metadata, AUTHENTICATION) } ?: builder
        }
        .connectTcp("localhost", port)
        .block(timeout)!!

    private fun unsupportedCredential(): ByteArray {
        val encoded = AuthMetadataCodec.encodeMetadata(
            ByteBufAllocator.DEFAULT,
            "x-chat-unsupported",
            Unpooled.copiedBuffer("some-credential", StandardCharsets.UTF_8),
        )
        return try {
            ByteBufUtil.getBytes(encoded)
        } finally {
            encoded.release()
        }
    }

    private fun roomExists(name: String): Boolean =
        composite.topicService().getRoomByName(ByStringRequest(name))
            .map { true }
            .onErrorReturn(false)
            .block(timeout)!!

    private fun ownersOf(room: String): List<Key<Long>> {
        val roomKey = composite.topicService().getRoomByName(ByStringRequest(room)).block(timeout)!!.key
        return stores.authMetaPersistence().all().collectList().block(timeout)!!
            .filter { it.target.id == roomKey.id && it.permission == "*" && !it.mute }
            .map { it.principal }
    }

    private fun keyOf(handle: String): Key<Long> =
        composite.userService().findByUsername(ByStringRequest(handle))
            .filter { it.handle == handle }.single().block(timeout)!!.key

    companion object {
        private val AUTHENTICATION =
            MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string)
        private val LEGACY_BASIC = MimeTypeUtils.parseMimeType("message/x.rsocket.authentication.basic.v0")
        private val signingKeyPath = AgentTestTokens.createKey()
        private val foreignKeyPath = AgentTestTokens.createKey()

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.security.jwt.jwk-path") { signingKeyPath }
        }
    }
}
