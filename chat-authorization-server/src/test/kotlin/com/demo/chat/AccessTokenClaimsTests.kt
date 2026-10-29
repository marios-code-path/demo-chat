package com.demo.chat

import com.demo.chat.config.agent.AgentJwtDecoderFactory
import com.demo.chat.config.deploy.authserv.AgentClients
import com.demo.chat.config.deploy.authserv.Oauth2ClientProperties
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
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID

/**
 * The tokens that the agent clients receive. See `CHAT-pgpmsgvr` and
 * `CHAT-frcrctdp`.
 *
 * Each agent client takes a generated secret, which the start prints once.
 * The output capture reads it back.
 */
@ExtendWith(OutputCaptureExtension::class)
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
        "app.oauth2.agents[0].client-id=client-agent",
        "app.oauth2.agents[0].username=Agent",
        "app.oauth2.agents[1].client-id=client-claude",
        "app.oauth2.agents[1].username=Claude",
    ],
)
@ActiveProfiles("memory")
class AccessTokenClaimsTests {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var clientProperties: Oauth2ClientProperties

    @Autowired
    private lateinit var registeredClients: RegisteredClientRepository

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun signingKey(registry: DynamicPropertyRegistry) {
            AuthorizationServerTestSigningKey.register(registry)
        }
    }

    private fun secretOf(output: CapturedOutput, clientId: String): String =
        Regex("Generated secret for agent client '$clientId' \\(\\w+\\): (\\S+)")
            .findAll(output.out).last().groupValues[1]

    private fun tokenResponse(clientId: String, secret: String, scope: String): Pair<Int, Map<*, *>> =
        WebClient.create("http://localhost:$port")
            .post()
            .uri("/oauth2/token")
            .headers { it.setBasicAuth(clientId, secret) }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData("grant_type", "client_credentials").with("scope", scope))
            .exchangeToMono { response ->
                response.bodyToMono(Map::class.java).map { response.statusCode().value() to it }
            }
            .block()!!

    private fun tokenBody(output: CapturedOutput, clientId: String): Map<*, *> {
        val (status, body) = tokenResponse(clientId, secretOf(output, clientId), "chat.mcp")
        assertThat(status).describedAs("the token status of $clientId").isEqualTo(200)
        return body
    }

    private fun accessToken(output: CapturedOutput, clientId: String): String =
        tokenBody(output, clientId)["access_token"] as String

    @Test
    fun `a signed private key assertion receives an agent access token`() {
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
            val clientId = "private-agent-${UUID.randomUUID()}"
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
            val response = tokenRequest(clientId, assertion.serialize())

            assertThat(response.first).describedAs("token response: ${response.second}").isEqualTo(200)
            val accessToken = response.second["access_token"] as String
            assertThat(SignedJWT.parse(accessToken).jwtClaimsSet.getStringClaim("client_id")).isEqualTo(clientId)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a repeated client assertion is refused`() {
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
            val clientId = "replay-agent-${UUID.randomUUID()}"
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

    @Test
    fun `an access token carries the client id claim`(output: CapturedOutput) {
        val claims = SignedJWT.parse(accessToken(output, "client-agent")).jwtClaimsSet

        assertThat(claims.getStringClaim("client_id")).isEqualTo("client-agent")
    }

    @Test
    fun `an access token carries the requested scope`(output: CapturedOutput) {
        val claims = SignedJWT.parse(accessToken(output, "client-agent")).jwtClaimsSet

        assertThat(claims.getStringListClaim("scope")).contains("chat.mcp")
    }

    @Test
    fun `measure the audience claim`(output: CapturedOutput) {
        val claims = SignedJWT.parse(accessToken(output, "client-agent")).jwtClaimsSet

        println("MEASURED aud = ${claims.audience}")
        println("MEASURED sub = ${claims.subject}")
        println("MEASURED client_id = ${claims.getStringClaim("client_id")}")
    }

    @Test
    fun `client credentials does not issue a refresh token`(output: CapturedOutput) {
        assertThat(tokenBody(output, "client-agent").keys).doesNotContain("refresh_token")
    }

    /**
     * The agent decoder accepts each token. That is the decoder that REST and
     * the core build from the trusted JWK. See `CHAT-frcrctdp`.
     */
    @Test
    fun `the agent decoder accepts each agent token for its own client id`(output: CapturedOutput) {
        val decoder = AgentJwtDecoderFactory.fromJwkFile(AuthorizationServerTestSigningKey.path())

        listOf("client-agent", "client-claude").forEach { clientId ->
            val jwt = decoder.decode(accessToken(output, clientId)).block()!!

            assertThat(jwt.claims["client_id"]).isEqualTo(clientId)
            assertThat(jwt.getClaimAsStringList("scope")).containsExactly("chat.mcp")
            assertThat(Duration.between(jwt.issuedAt, jwt.expiresAt)).isEqualTo(Duration.ofSeconds(300))
        }
    }

    @Test
    fun `the configured client no longer receives the agent scope`() {
        val (status, body) = tokenResponse(clientProperties.clientId, "secret", "chat.mcp")

        assertThat(status).isEqualTo(400)
        assertThat(body["error"]).isEqualTo("invalid_scope")
    }
}
