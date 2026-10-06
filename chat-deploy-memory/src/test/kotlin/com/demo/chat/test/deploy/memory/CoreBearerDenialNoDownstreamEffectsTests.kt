package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.config.controller.core.PersistenceControllersConfiguration.AuthMetaPersistenceController
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.security.AuthMetaPersistence
import com.demo.chat.service.security.SecretsStore
import io.rsocket.exceptions.CustomRSocketException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.rsocket.metadata.BearerTokenAuthenticationEncoder
import org.springframework.security.rsocket.metadata.BearerTokenMetadata
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.util.MimeTypeUtils
import org.springframework.context.ApplicationContext
import org.springframework.boot.rsocket.server.RSocketServerCustomizer
import org.springframework.boot.security.autoconfigure.rsocket.RSocketSecurityAutoConfiguration
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor
import com.demo.chat.security.rsocket.RSocketSecurityErrorCodes
import reactor.test.StepVerifier
import java.time.Duration
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.verifyNoInteractions

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-core-bearer-denial-no-downstream-effects",
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
        "app.init.initial-users.Agent.password=core-bearer-agent-secret",
        "app.security.required-scope=chat.mcp",
        "app.security.agents[0].client-id=client-under-test",
        "app.security.agents[0].username=Agent",
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoreBearerDenialNoDownstreamEffectsTests {

    @Autowired lateinit var strategies: RSocketStrategies
    @Autowired lateinit var context: ApplicationContext
    @MockitoSpyBean lateinit var controller: AuthMetaPersistenceController<*, *>
    @MockitoSpyBean(name = "authMetaPersistence") lateinit var persistence: AuthMetaPersistence<*>
    @MockitoSpyBean(name = "authMetadataIndex") lateinit var index: AuthMetaIndex<*, *>
    @MockitoSpyBean(name = "secretsStore") lateinit var secrets: SecretsStore<*>
    @MockitoSpyBean(name = "pubSubService") lateinit var pubsub: TopicPubSubService<*, *>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val timeout = Duration.ofSeconds(10)

    @BeforeEach
    fun clearDownstreamInvocations() {
        clearInvocations(controller, persistence, index, secrets, pubsub)
    }

    @Test
    fun `the production application installs one security customizer and interceptor`() {
        assertThat(context.getBeansOfType(RSocketServerCustomizer::class.java).keys)
            .contains("rsocketSecurityServerCustomizer")
        assertThat(context.getBeansOfType(RSocketSecurityAutoConfiguration::class.java)).isEmpty()
        assertThat(context.getBeansOfType(PayloadSocketAcceptorInterceptor::class.java)).hasSize(1)
    }

    @ParameterizedTest
    @ValueSource(strings = ["expired", "wrong-client"])
    fun `an invalid bearer is refused before the controller and downstream services run`(refusal: String) {
        val requester = RSocketRequester.builder()
            .rsocketStrategies(
                strategies.mutate().encoders { it.add(0, BearerTokenAuthenticationEncoder()) }.build()
            )
            .connectTcp("localhost", port)
            .block(timeout)!!

        try {
            StepVerifier.create(
                requester.route("persist.authmetadata.all")
                    .metadata(
                        BearerTokenMetadata(CoreBearerTestToken.mint(signingKeyPath, refusal)),
                        MimeTypeUtils.parseMimeType("message/x.rsocket.authentication.v0"),
                    )
                    .retrieveFlux(Map::class.java)
            ).expectErrorSatisfies { error ->
                assertThat(error).isInstanceOf(CustomRSocketException::class.java)
                assertThat((error as CustomRSocketException).errorCode())
                    .isEqualTo(RSocketSecurityErrorCodes.AUTHENTICATION)
            }.verify(timeout)

            verifyNoInteractions(controller, persistence, index, secrets, pubsub)
        } finally {
            requester.dispose()
        }
    }

    /**
     * The control for the scope refusal. A valid agent token reaches the
     * composite route, so a refusal there comes from the scope alone.
     */
    @Test
    fun `an agent bearer with the required scope adds a room`() {
        val requester = bearerRequester()
        try {
            StepVerifier.create(
                requester.route("topic.topic-add")
                    .metadata(
                        BearerTokenMetadata(CoreBearerTestToken.mint(signingKeyPath, "valid")),
                        BEARER_MIME_TYPE,
                    )
                    .data(ByStringRequest("scopecontrol"))
                    .retrieveMono(Map::class.java)
            ).expectNextCount(1).verifyComplete()
        } finally {
            requester.dispose()
        }
    }

    @Test
    fun `an agent bearer without the required scope is refused before the composite service runs`() {
        val requester = bearerRequester()
        try {
            StepVerifier.create(
                requester.route("topic.topic-add")
                    .metadata(
                        BearerTokenMetadata(CoreBearerTestToken.mint(signingKeyPath, "wrong-scope")),
                        BEARER_MIME_TYPE,
                    )
                    .data(ByStringRequest("scoperefused"))
                    .retrieveMono(Map::class.java)
            ).expectErrorSatisfies { error ->
                assertThat(error).isInstanceOf(CustomRSocketException::class.java)
                assertThat((error as CustomRSocketException).errorCode())
                    .isEqualTo(RSocketSecurityErrorCodes.AUTHORIZATION)
            }.verify(timeout)

            verifyNoInteractions(persistence, index, secrets, pubsub)
        } finally {
            requester.dispose()
        }
    }

    private fun bearerRequester(): RSocketRequester = RSocketRequester.builder()
        .rsocketStrategies(
            strategies.mutate().encoders { it.add(0, BearerTokenAuthenticationEncoder()) }.build()
        )
        .connectTcp("localhost", port)
        .block(timeout)!!

    companion object {
        private val BEARER_MIME_TYPE = MimeTypeUtils.parseMimeType("message/x.rsocket.authentication.v0")
        private val signingKeyPath = CoreBearerTestToken.createKey()

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.security.jwt.jwk-path") { signingKeyPath }
        }
    }
}

private object CoreBearerTestToken {

    fun createKey(): String {
        val key = com.nimbusds.jose.jwk.gen.ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256)
            .keyID("core-bearer-test")
            .generate()
        val file = java.nio.file.Files.createTempFile("core-bearer-test", ".jwk")
        file.toFile().deleteOnExit()
        java.nio.file.Files.writeString(file, key.toJSONString())
        return file.toString()
    }

    fun mint(path: String, refusal: String): String {
        val key = com.nimbusds.jose.jwk.JWK.parse(java.nio.file.Files.readString(java.nio.file.Paths.get(path)))
            as com.nimbusds.jose.jwk.ECKey
        val claims = com.nimbusds.jwt.JWTClaimsSet.Builder()
            .issuer("https://authserv")
            .subject("client-under-test")
            .claim("client_id", if (refusal == "wrong-client") "another-client" else "client-under-test")
            .claim("scope", if (refusal == "wrong-scope") "openid" else "chat.mcp")
            .issueTime(java.util.Date(System.currentTimeMillis() - 120_000))
            .expirationTime(java.util.Date(System.currentTimeMillis() + if (refusal == "expired") -60_000 else 60_000))
            .build()
        val token = com.nimbusds.jwt.SignedJWT(
            com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.ES256)
                .keyID(key.keyID).build(),
            claims,
        )
        token.sign(com.nimbusds.jose.crypto.ECDSASigner(key))
        return token.serialize()
    }
}
