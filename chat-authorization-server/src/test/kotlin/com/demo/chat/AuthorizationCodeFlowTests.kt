package com.demo.chat

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.util.UriComponentsBuilder

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
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
        "app.rsocket.transport.security.type=unprotected"
    ]
)
@AutoConfigureMockMvc
@ActiveProfiles("memory")
class AuthorizationCodeFlowTests {

    companion object {
        private const val AUTHORIZATION_ENDPOINT = "/oauth2/authorize"
        private const val TOKEN_ENDPOINT = "/oauth2/token"
        private const val CLIENT_REGISTRATION_ID = "authorization-code-test-registration"
        private const val CLIENT_ID = "authorization-code-test-client"
        private const val CLIENT_SECRET = "secret"
        private const val REDIRECT_URI = "https://client.example.test/callback"
        private const val TEST_USER = "token-test-user"
        private const val STATE = "authorization-code-test-state"
        private const val OPENID_SCOPE = "openid"
        private const val PROFILE_SCOPE = "profile"
        private val CONSENT_STATE_PATTERN = Regex("""name="state" value="([^"]+)"""")

        @JvmStatic
        @DynamicPropertySource
        fun testProperties(registry: DynamicPropertyRegistry) {
            AuthorizationServerTestSigningKey.register(registry)
            registry.add("app.oauth2.client.id") { CLIENT_REGISTRATION_ID }
            registry.add("app.oauth2.client.client-id") { CLIENT_ID }
            registry.add("app.oauth2.client.secret") { "{noop}$CLIENT_SECRET" }
            registry.add("app.oauth2.client.redirect-uris[0]") { REDIRECT_URI }
            registry.add("app.oauth2.client.additional-scopes[0]") { OPENID_SCOPE }
            registry.add("app.oauth2.client.additional-scopes[1]") { PROFILE_SCOPE }
            registry.add("app.oauth2.client.client-authentication-methods[0]") {
                "client_secret_basic"
            }
            registry.add("app.oauth2.client.authorization-grant-types[0]") {
                AuthorizationGrantType.AUTHORIZATION_CODE.value
            }
            registry.add("app.oauth2.client.requires-authorization-concent") { true }
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun authorizationCodeFlowIssuesAndDecodesTokens() {
        val authorizationResult = mockMvc.perform(
            get(AUTHORIZATION_ENDPOINT)
                .with(user(TEST_USER))
                .queryParam(OAuth2ParameterNames.RESPONSE_TYPE, "code")
                .queryParam(OAuth2ParameterNames.CLIENT_ID, CLIENT_ID)
                .queryParam(OAuth2ParameterNames.REDIRECT_URI, REDIRECT_URI)
                .queryParam(OAuth2ParameterNames.SCOPE, "$OPENID_SCOPE $PROFILE_SCOPE")
                .queryParam(OAuth2ParameterNames.STATE, STATE)
        )
            .andExpect(status().isOk)
            .andReturn()

        val session = authorizationResult.request.session as MockHttpSession
        val consentState = requireNotNull(
            CONSENT_STATE_PATTERN.find(authorizationResult.response.contentAsString)
                ?.groupValues
                ?.get(1)
        )

        val consentResult = mockMvc.perform(
            post(AUTHORIZATION_ENDPOINT)
                .session(session)
                .with(user(TEST_USER))
                .param(OAuth2ParameterNames.CLIENT_ID, CLIENT_ID)
                .param(OAuth2ParameterNames.STATE, consentState)
                .param(OAuth2ParameterNames.SCOPE, OPENID_SCOPE, PROFILE_SCOPE)
        )
            .andExpect(status().is3xxRedirection)
            .andReturn()

        val redirect = requireNotNull(consentResult.response.redirectedUrl)
        assertThat(redirect.substringBefore("?")).isEqualTo(REDIRECT_URI)

        val redirectParameters = UriComponentsBuilder.fromUriString(redirect)
            .build()
            .queryParams
        assertThat(redirectParameters.getFirst(OAuth2ParameterNames.STATE)).isEqualTo(STATE)
        assertThat(redirectParameters[OAuth2ParameterNames.CODE]).hasSize(1)
        val authorizationCode = requireNotNull(
            redirectParameters.getFirst(OAuth2ParameterNames.CODE)
        )

        val tokenResult = mockMvc.perform(
            post(TOKEN_ENDPOINT)
                .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param(
                    OAuth2ParameterNames.GRANT_TYPE,
                    AuthorizationGrantType.AUTHORIZATION_CODE.value
                )
                .param(OAuth2ParameterNames.CODE, authorizationCode)
                .param(OAuth2ParameterNames.REDIRECT_URI, REDIRECT_URI)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.access_token").isNotEmpty)
            .andExpect(jsonPath("$.id_token").isNotEmpty)
            .andReturn()

        val tokenResponse = objectMapper.readTree(tokenResult.response.contentAsByteArray)
        val accessToken = tokenResponse.path(OAuth2ParameterNames.ACCESS_TOKEN).asText()
        val idToken = tokenResponse.path(OidcParameterNames.ID_TOKEN).asText()

        val accessJwt = jwtDecoder.decode(accessToken)
        val idJwt = jwtDecoder.decode(idToken)

        assertThat(accessJwt.headers["alg"]).hasToString("ES256")
        assertThat(accessJwt.subject).isEqualTo(TEST_USER)
        assertThat(idJwt.subject).isEqualTo(TEST_USER)
        assertThat(accessJwt.getClaimAsStringList(OAuth2ParameterNames.SCOPE))
            .containsExactlyInAnyOrder(OPENID_SCOPE, PROFILE_SCOPE)
    }
}
