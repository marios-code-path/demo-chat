package com.demo.chat

import com.demo.chat.config.deploy.authserv.AgentClients
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID

/**
 * Test a private key agent with the JDBC stores. See `CHAT-wdvjqtpb`.
 *
 * This test starts the `jdbc` profile.
 * It runs the four deployment schema files on H2 in PostgreSQL mode.
 * The test reads the replay and client rows after it requests a token.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [ChatApp::class, TestConfig::class, RequiredAppBeans::class],
    properties = [
        "spring.config.location=classpath:application.yml",
        "app.key.type=long",
        "app.client.protocol=rsocket",
        "app.primary=authserv_test",
        "app.rsocket.transport.unprotected",
        "app.client.rsocket.composite.user",
        "app.client.rsocket.composite.message",
        "app.client.rsocket.composite.topic",
        "app.client.rsocket.core.persistence",
        "app.client.rsocket.core.index",
        "app.service.composite.auth=true",
        "app.rsocket.transport.security.type=unprotected",
        "app.oauth2.agent-scope=chat.mcp",
        // A JWKS agent requires the replay table.
        "app.oauth2.agents[0].client-id=jdbc-agent",
        "app.oauth2.agents[0].username=Agent",
        "app.oauth2.agents[0].jwk-set-uri=https://agents.example/jwks",
    ],
)
@ActiveProfiles("jdbc")
class JdbcPrivateKeyAgentTests {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var registeredClients: RegisteredClientRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    companion object {
        private const val DATABASE = "jdbc:h2:mem:authserv-jdbc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"

        @JvmStatic
        @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            AuthorizationServerTestSigningKey.register(registry)
            registry.add("spring.datasource.url") { DATABASE }
            registry.add("spring.datasource.username") { "sa" }
            registry.add("spring.datasource.password") { "" }
            registry.add("spring.sql.init.mode") { "always" }
            registry.add("spring.sql.init.schema-locations") {
                listOf(
                    "oauth2-registered-client-schema.sql",
                    "oauth2-authorization-schema.sql",
                    "oauth2-authorization-consent-schema.sql",
                    "oauth2-client-assertion-jti-schema.sql",
                ).joinToString(",") { "file:sql-scripts/$it" }
            }
        }
    }

    @Test
    fun `a signed assertion receives a token once`() {
        val key = RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate()
        val jwks = JWKSet(key.toPublicJWK()).toString().toByteArray(StandardCharsets.UTF_8)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/jwks") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, jwks.size.toLong())
            exchange.responseBody.use { it.write(jwks) }
        }
        server.start()
        try {
            val clientId = "jdbc-private-${UUID.randomUUID()}"
            registeredClients.save(
                AgentClients.buildPrivateKeyJwt(clientId, "chat.mcp", "http://127.0.0.1:${server.address.port}/jwks")
            )
            val now = Date()
            val assertion = SignedJWT(
                JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(),
                JWTClaimsSet.Builder()
                    .issuer(clientId).subject(clientId)
                    .audience("http://localhost:$port/oauth2/token")
                    .issueTime(now).expirationTime(Date(now.time + 60_000))
                    .jwtID(UUID.randomUUID().toString()).build(),
            )
            assertion.sign(RSASSASigner(key))

            val first = tokenRequest(clientId, assertion.serialize())
            assertThat(first.first).describedAs("first response: ${first.second}").isEqualTo(200)
            assertThat(first.second["access_token"]).isNotNull()

            // The replay row proves that the JDBC store handled the request.
            assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM oauth2_client_assertion_jti WHERE client_id = ?",
                    Long::class.java, clientId,
                )
            ).isEqualTo(1L)
            assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM oauth2_registered_client WHERE client_id = ?",
                    Long::class.java, clientId,
                )
            ).isEqualTo(1L)

            val second = tokenRequest(clientId, assertion.serialize())
            assertThat(second.first).describedAs("replay response: ${second.second}").isEqualTo(401)
            assertThat(second.second["error"]).isEqualTo("invalid_client")
        } finally {
            server.stop(0)
        }
    }

    private fun tokenRequest(clientId: String, assertion: String): Pair<Int, Map<*, *>> =
        WebClient.create("http://localhost:$port").post().uri("/oauth2/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                .with("client_id", clientId)
                .with("scope", "chat.mcp")
                .with("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                .with("client_assertion", assertion))
            .exchangeToMono { reply ->
                reply.bodyToMono(Map::class.java).map { reply.statusCode().value() to it }
            }.block()!!
}
