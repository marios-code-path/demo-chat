# Authorization-Code Nimbus Regression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans`. Do not use subagent-driven development. Steps use checkbox syntax for tracking.

**Goal:** Add a default-lane authorization-code test that exercises Nimbus token signing and decoding.

**Architecture:** Keep the random-port deploy smoke test unchanged. Add a separate MockMvc context for the protocol flow and share one temporary signing key.

**Tech Stack:** Kotlin, JUnit 5, Spring Boot Test, Spring Security Test, Spring Authorization Server, Jackson, AssertJ, Maven, Java 25.

**Issue:** `CHAT-ijozotvi`

**Specification:** `docs/superpowers/specs/2026-09-10-authorization-code-nimbus-regression-design.md`

---

### Task 1: Share the signing key and state required beans

**Files:**

- Create: `chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationServerTestSigningKey.kt`
- Modify: `chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationServerDeployTests.kt`
- Test: `chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationServerDeployTests.kt`

- [ ] **Step 1: Check drift bindings**

Run:

```bash
drift refs chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationServerDeployTests.kt
```

Expected: no bindings.

- [ ] **Step 2: Create the shared signing-key helper**

Create `AuthorizationServerTestSigningKey.kt` with this content:

```kotlin
package com.demo.chat

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import org.springframework.test.context.DynamicPropertyRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Creates one temporary ES256 key for authorization-server tests.
 *
 * The repository does not commit `server_keycert.jwk`.
 * No build step runs `gen-dckeys.sh`.
 * The test key has no x5c chain because these tests do not validate one.
 */
internal object AuthorizationServerTestSigningKey {
    private val jwkLocation = generateSigningKey().toUri().toString()

    fun register(registry: DynamicPropertyRegistry) {
        registry.add("app.oauth2.jwk.path") { jwkLocation }
    }

    private fun generateSigningKey(): Path {
        val jwk = ECKeyGenerator(Curve.P_256)
            .keyID(UUID.randomUUID().toString())
            .generate()

        val file = Files.createTempFile("authserver-test-signing-key", ".jwk")
        file.toFile().deleteOnExit()
        Files.writeString(file, jwk.toJSONString())
        return file
    }
}
```

- [ ] **Step 3: Refactor the deploy test without changing its behavior**

Replace `AuthorizationServerDeployTests.kt` with this content:

```kotlin
package com.demo.chat

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.IndexSearchRequestConverters
import com.demo.chat.domain.RequestToQueryConverters
import com.demo.chat.domain.TypeUtil
import com.demo.chat.security.service.CoreUserDetailsService
import com.demo.chat.service.client.ClientDiscovery
import com.demo.chat.service.client.discovery.LocalhostDiscovery
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [ChatApp::class, TestConfig::class],
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
@ActiveProfiles("memory")
class AuthorizationServerDeployTests {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun signingKey(registry: DynamicPropertyRegistry) {
            AuthorizationServerTestSigningKey.register(registry)
        }
    }

    @Test
    fun contextLoads() {
    }
}

@TestConfiguration
class TestConfig(
    private val typeUtil: TypeUtil<Long>,
    private val coreUserDetailsService: CoreUserDetailsService<Long>
) {
    @Bean
    fun localDiscovery(): ClientDiscovery = LocalhostDiscovery("127.0.0.1", 9000)

    @Bean
    fun requestToQueryConverters(): RequestToQueryConverters<IndexSearchRequest> =
        IndexSearchRequestConverters()
}
```

The constructor parameters state the required application beans. Do not remove them because an IDE reports no direct use.

- [ ] **Step 4: Run the deploy smoke test**

Run:

```bash
JAVA_HOME=/Users/darkbit1001/.sdkman/candidates/java/25.0.4-tem \
PATH=/Users/darkbit1001/.sdkman/candidates/java/25.0.4-tem/bin:$PATH \
mvn -o -B -pl chat-authorization-server -am \
  -Dtest=AuthorizationServerDeployTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `AuthorizationServerDeployTests` runs one test with no failures.

- [ ] **Step 5: Commit the fixture refactor**

Run:

```bash
git add \
  chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationServerTestSigningKey.kt \
  chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationServerDeployTests.kt
git commit -m "test: share authorization-server signing setup"
```

### Task 2: Prove the new protocol test enters the focused lane

**Files:**

- Create: `chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationCodeFlowTests.kt`
- Test: `chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationCodeFlowTests.kt`

- [ ] **Step 1: Add a deliberate failing protocol test**

Create `AuthorizationCodeFlowTests.kt` with this content:

```kotlin
package com.demo.chat

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = [ChatApp::class, TestConfig::class],
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
        private const val CLIENT_REGISTRATION_ID = "authorization-code-test-registration"
        private const val CLIENT_ID = "authorization-code-test-client"
        private const val CLIENT_SECRET = "secret"
        private const val REDIRECT_URI = "https://client.example.test/callback"

        @JvmStatic
        @DynamicPropertySource
        fun testProperties(registry: DynamicPropertyRegistry) {
            AuthorizationServerTestSigningKey.register(registry)
            registry.add("app.oauth2.client.id") { CLIENT_REGISTRATION_ID }
            registry.add("app.oauth2.client.client-id") { CLIENT_ID }
            registry.add("app.oauth2.client.secret") { "{noop}$CLIENT_SECRET" }
            registry.add("app.oauth2.client.redirect-uris[0]") { REDIRECT_URI }
            registry.add("app.oauth2.client.additional-scopes[0]") { "openid" }
            registry.add("app.oauth2.client.additional-scopes[1]") { "profile" }
            registry.add("app.oauth2.client.client-authentication-methods[0]") {
                "client_secret_basic"
            }
            registry.add("app.oauth2.client.authorization-grant-types[0]") {
                "authorization_code"
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
        fail("Authorization-code flow is not implemented")
    }
}
```

- [ ] **Step 2: Run both test classes and verify the deliberate failure**

Run:

```bash
JAVA_HOME=/Users/darkbit1001/.sdkman/candidates/java/25.0.4-tem \
PATH=/Users/darkbit1001/.sdkman/candidates/java/25.0.4-tem/bin:$PATH \
mvn -o -B -pl chat-authorization-server -am \
  -Dtest='AuthorizationCodeFlowTests,AuthorizationServerDeployTests' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `AuthorizationServerDeployTests` passes. `AuthorizationCodeFlowTests` fails with the deliberate message.

Do not commit the deliberate failure.

### Task 3: Complete the authorization-code flow

**Files:**

- Modify: `chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationCodeFlowTests.kt`
- Test: `chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationCodeFlowTests.kt`
- Verify: `docs/superpowers/specs/2026-09-10-authorization-code-nimbus-regression-design.md`

- [ ] **Step 1: Replace the deliberate failure with the endpoint flow**

Replace `AuthorizationCodeFlowTests.kt` with this content:

```kotlin
package com.demo.chat

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.util.UriComponentsBuilder

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = [ChatApp::class, TestConfig::class],
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
                .param(OAuth2ParameterNames.RESPONSE_TYPE, "code")
                .param(OAuth2ParameterNames.CLIENT_ID, CLIENT_ID)
                .param(OAuth2ParameterNames.REDIRECT_URI, REDIRECT_URI)
                .param(OAuth2ParameterNames.SCOPE, OPENID_SCOPE, PROFILE_SCOPE)
                .param(OAuth2ParameterNames.STATE, STATE)
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Consent required")))
            .andReturn()

        val session = authorizationResult.request.session as MockHttpSession

        val consentResult = mockMvc.perform(
            post(AUTHORIZATION_ENDPOINT)
                .session(session)
                .with(user(TEST_USER))
                .param(OAuth2ParameterNames.CLIENT_ID, CLIENT_ID)
                .param(OAuth2ParameterNames.STATE, STATE)
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

        assertThat(accessJwt.headers["alg"]).isEqualTo("ES256")
        assertThat(accessJwt.subject).isEqualTo(TEST_USER)
        assertThat(idJwt.subject).isEqualTo(TEST_USER)
        assertThat(accessJwt.getClaimAsStringList(OAuth2ParameterNames.SCOPE))
            .containsExactlyInAnyOrder(OPENID_SCOPE, PROFILE_SCOPE)
    }
}
```

Do not add CSRF to the consent request. The authorization-server endpoint matcher already ignores it.

Do not send `redirect_uri` with the consent request. Send it only with authorization and token requests.

- [ ] **Step 2: Run both focused test classes**

Run:

```bash
JAVA_HOME=/Users/darkbit1001/.sdkman/candidates/java/25.0.4-tem \
PATH=/Users/darkbit1001/.sdkman/candidates/java/25.0.4-tem/bin:$PATH \
mvn -o -B -pl chat-authorization-server -am \
  -Dtest='AuthorizationCodeFlowTests,AuthorizationServerDeployTests' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: both test classes pass. The authorization-server module reports two tests with no failures.

If a linkage error occurs, preserve the test. Record the exact Nimbus or Spring Security call before changing a dependency.

- [ ] **Step 3: Run repository checks**

Run:

```bash
git diff --check
drift check
```

Expected: `git diff --check` prints nothing. `drift check` prints `ok`.

- [ ] **Step 4: Run the default reactor**

Run:

```bash
JAVA_HOME=/Users/darkbit1001/.sdkman/candidates/java/25.0.4-tem \
PATH=/Users/darkbit1001/.sdkman/candidates/java/25.0.4-tem/bin:$PATH \
mvn -B clean test
```

Expected: the reactor succeeds. The new protocol test runs in the default lane.

- [ ] **Step 5: Commit the protocol regression test**

Run:

```bash
git add chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationCodeFlowTests.kt
git commit -m "test: exercise authorization-code token exchange"
```

### Task 4: Verify the cumulative branch state

**Files:**

- Verify: `chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationServerTestSigningKey.kt`
- Verify: `chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationServerDeployTests.kt`
- Verify: `chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationCodeFlowTests.kt`
- Verify: `docs/superpowers/specs/2026-09-10-authorization-code-nimbus-regression-design.md`

- [ ] **Step 1: Check the cumulative diff**

Run:

```bash
git diff --check origin/master...HEAD
git diff --stat origin/master...HEAD
git status --short --branch
```

Expected: the diff check prints nothing. The status reports no uncommitted files.

- [ ] **Step 2: Run the final focused proof**

Run:

```bash
JAVA_HOME=/Users/darkbit1001/.sdkman/candidates/java/25.0.4-tem \
PATH=/Users/darkbit1001/.sdkman/candidates/java/25.0.4-tem/bin:$PATH \
mvn -o -B -pl chat-authorization-server -am \
  -Dtest='AuthorizationCodeFlowTests,AuthorizationServerDeployTests' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: both classes pass with no failures or errors.

- [ ] **Step 3: Verify issue scope**

Run:

```bash
FP_AGENT_NAME='sigma' fp issue show CHAT-ijozotvi
FP_AGENT_NAME='sigma' fp issue show CHAT-cvdcfczj
```

Expected: `CHAT-ijozotvi` owns the narrow regression. `CHAT-cvdcfczj` owns the deferred contract.
