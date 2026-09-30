package com.demo.chat

import com.demo.chat.config.deploy.authserv.Oauth2ClientProperties
import com.nimbusds.jwt.SignedJWT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient

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
        "app.service.composite.auth",
        "app.rsocket.transport.security.type=unprotected",
    ],
)
@ActiveProfiles("memory")
class AccessTokenClaimsTests {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var clientProperties: Oauth2ClientProperties

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun signingKey(registry: DynamicPropertyRegistry) {
            AuthorizationServerTestSigningKey.register(registry)
        }
    }

    private fun accessToken(): String {
        val response = WebClient.create("http://localhost:$port")
            .post()
            .uri("/oauth2/token")
            .headers { it.setBasicAuth(clientProperties.clientId, "secret") }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("grant_type", "client_credentials")
                    .with("scope", "chat.mcp")
            )
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()!!

        return response["access_token"] as String
    }

    @Test
    fun `an access token carries the client id claim`() {
        val claims = SignedJWT.parse(accessToken()).jwtClaimsSet

        assertThat(claims.getStringClaim("client_id")).isEqualTo(clientProperties.clientId)
    }

    @Test
    fun `an access token carries the requested scope`() {
        val claims = SignedJWT.parse(accessToken()).jwtClaimsSet

        assertThat(claims.getStringListClaim("scope")).contains("chat.mcp")
    }

    @Test
    fun `measure the audience claim`() {
        val claims = SignedJWT.parse(accessToken()).jwtClaimsSet

        println("MEASURED aud = ${claims.audience}")
        println("MEASURED sub = ${claims.subject}")
        println("MEASURED client_id = ${claims.getStringClaim("client_id")}")
    }

    @Test
    fun `client credentials does not issue a refresh token`() {
        val response = WebClient.create("http://localhost:$port")
            .post()
            .uri("/oauth2/token")
            .headers { it.setBasicAuth(clientProperties.clientId, "secret") }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("grant_type", "client_credentials")
                    .with("scope", "chat.mcp")
            )
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()!!

        assertThat(response.keys).doesNotContain("refresh_token")
    }
}
