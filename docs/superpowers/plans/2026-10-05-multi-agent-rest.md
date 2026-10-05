# More Than One Agent on One REST Deployment: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task by task, inline. `AGENTS.md` forbids subagent-driven development in this repository. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The token `client_id` selects one of several agent identities on REST and on the core, and the authorization server issues one client per agent.

**Architecture:** `AgentSecurityProperties` binds a list of agents and one scope. `AgentIdentities` maps a client id to a resolved principal, and the shared `AgentAuthenticationConverter` looks the client id up. `chat-build --agent CLIENT_ID=HANDLE` emits the list for `core` and `rest`, the agent users for `core`, and the agent clients for `authserv`. The authorization server builds one `client_credentials` client per agent with a generated secret.

**Tech Stack:** Kotlin 2.4, Spring Boot 4.0.8, Spring Security 7, Spring Authorization Server 7, Python 3 (`chat-build`), Bash (`test-flags.sh`).

**Spec:** `docs/superpowers/specs/2026-10-05-multi-agent-rest-design.md`. Read it before you start. This plan argues from it.

## Global Constraints

- The scope stays one value. This deployment selects `chat.mcp`.
- The old keys `app.security.agent.*` fail the start. There is no alias period.
- Reserved handles: `Admin`, `Anon`, and every handle in `app.security.service-accounts` (default `Service`).
- Handle comparison for reserved names and duplicates uses `lowercase(Locale.ROOT)`. Client id comparison is exact.
- A `chat-build` handle holds `[A-Za-z0-9_]+` alone.
- Core user flags use the bracket form `app.init.initial-users[<H>].<field>`. Relaxed binding reads it the same as the spec form `app.init.initialUsers[<H>]`.
- The agent client shape: grant `client_credentials` alone, `client_secret_basic` alone, scope set = `app.oauth2.agent-scope` alone, no consent, secret prefix `{bcrypt}`, `SELF_CONTAINED` tokens, lifetime 300 seconds.
- Messages that a test asserts are copied verbatim from the spec.
- All prose follows ASD-STE100 strict mode: one instruction per sentence, no semicolons.
- Every commit message names `CHAT-frcrctdp` and ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Build output goes to a log file. Read the exit code and the summary lines alone. Use `$SCRATCH=/private/tmp/claude-501/-Users-darkbit1001-workspace-demo-chat/e28f0400-24b4-4585-b469-626a5d265b49/scratchpad`.
- Run one Maven build at a time in this checkout.
- Restore a mutation by absolute path, then prove it with `git status --short`.

## Review Focus

1. **A token whose `client_id` claim is a JSON number or a list.** The converter must refuse it with `BadCredentialsException`, as it refuses a missing claim. Task 1 keeps the existing non-string case and adds a list case.
2. **An agent handle that differs from a stored user by case alone, for example `agent` against `Agent`.** The lifecycle must keep zero users and fail the start. It must not select `Agent`. Task 1 owns the test.
3. **A core launch with `--agent` but without the `users` init phase.** The start must fail and name the handle. It must not start with a missing agent. Task 1 owns the test: `a missing second handle fails the start and names it` is the lifecycle reading of that launch.
4. **An authorization server start with agents but no `app.oauth2.agent-scope`.** The start must fail and name the property. It must not register clients with no scope. Task 5 owns the test.
5. **An environment variable form of an old key, `APP_SECURITY_AGENT_CLIENTID`.** The guard must refuse it as it refuses the dotted form. Task 2 owns the test.

---

## File Structure

| File | Responsibility |
|---|---|
| `chat-security/.../config/agent/AgentSecurityProperties.kt` | Modify. Binds `required-scope`, `agents`, `jwt`, `service-accounts`. Validates list, duplicates, reserved names. |
| `chat-security/.../config/agent/AgentSecurityPropertiesGuard.kt` | Create. Refuses the old `app.security.agent.*` keys from any property source. |
| `chat-security/.../config/agent/AgentIdentities.kt` | Create. Replaces `AgentIdentity.kt`. Client id to principal. |
| `chat-security/.../config/agent/AgentIdentity.kt` | Delete. |
| `chat-security/.../config/agent/AgentIdentityLifecycle.kt` | Modify. Resolves every agent, exact handle filter, one key per client. |
| `chat-security/.../config/agent/AgentAuthenticationConverter.kt` | Modify. Looks the client id up in `AgentIdentities`. |
| `chat-webflux/.../config/agent/AgentSecurityConfiguration.kt` | Modify. REST wiring. |
| `chat-webflux/.../config/agent/AgentResourceServerChain.kt` | Modify. Reads the scope from `Complete`. |
| `chat-service-controller/.../config/rsocket/RSocketAgentSecurityConfiguration.kt` | Modify. Core wiring. |
| `chat-authorization-server/.../config/deploy/authserv/AgentClientProperties.kt` | Create. Binds `app.oauth2.agents` and `app.oauth2.agent-scope`. |
| `chat-authorization-server/.../config/deploy/authserv/AgentClients.kt` | Create. Factory, shape check, collision check, registrar. |
| `chat-authorization-server/.../config/deploy/authserv/AuthorizationServerConfig.kt` | Modify. Memory repository holds agent clients. |
| `chat-authorization-server/.../config/deploy/authserv/ClientLoader.kt` | Modify. Registers every Boot client, then reconciles agent clients. |
| `shared-deploy-configuration/src/main/config/oauth2-client.yml` | Modify. `chat-client` loses `chat.mcp`. |
| `shell-scripts/chat-build` | Modify. `--agent`, refusals, emission. |
| `shell-scripts/test-flags.sh`, `shell-scripts/golden/*.flags` | Modify. Agent cases and refusal cases. |
| Tests, docs, register | See each task. |

---

### Task 1: The agent list, identity selection, and every caller

This task changes the model and every caller in one commit, because the old types stop compiling.

**Files:**
- Modify: `chat-security/src/main/kotlin/com/demo/chat/config/agent/AgentSecurityProperties.kt`
- Create: `chat-security/src/main/kotlin/com/demo/chat/config/agent/AgentIdentities.kt`
- Delete: `chat-security/src/main/kotlin/com/demo/chat/config/agent/AgentIdentity.kt`
- Modify: `chat-security/src/main/kotlin/com/demo/chat/config/agent/AgentIdentityLifecycle.kt`
- Modify: `chat-security/src/main/kotlin/com/demo/chat/config/agent/AgentAuthenticationConverter.kt`
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentSecurityConfiguration.kt`
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentResourceServerChain.kt:19`
- Modify: `chat-service-controller/src/main/kotlin/com/demo/chat/config/rsocket/RSocketAgentSecurityConfiguration.kt`
- Modify tests: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/AgentSecurityPropertiesTests.kt`, `AgentAuthenticationConverterTests.kt`, `AgentIdentityLifecycleTests.kt`, `AgentResourceServerChainTests.kt`, `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/WebFluxAnonymousIdentityTests.kt`
- Modify fixtures: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/security/BothChainsApplication.kt`, `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/denied/DeniedCallerApplication.kt`, `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/security/DeployTestSigningKey.kt`
- Modify: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/CoreBearerDenialNoDownstreamEffectsTests.kt:58-61`

**Interfaces:**
- Produces: `AgentSecurityProperties.requiredScope: String?`, `.agents: List<Agent>`, `.jwt: Jwt?`, `.serviceAccounts: List<String>`, `.isConfigured(): Boolean`, `.requireComplete(): Complete`.
- Produces: `AgentSecurityProperties.Complete(agents: List<Agent>, requiredScope: String, jwt: Jwt)` with `requiredAuthority(): String`.
- Produces: `AgentSecurityProperties.Agent` with `lateinit var clientId: String`, `lateinit var username: String`.
- Produces: `AgentIdentities.resolve(byClientId: Map<String, ChatUserDetails<*>>)`, `AgentIdentities.principalFor(clientId: String): ChatUserDetails<*>?`.
- Produces: `AgentAuthenticationConverter(identities: AgentIdentities)`.
- Produces: `AgentIdentityLifecycle<T>(users: ChatUserService<T>, identities: AgentIdentities, properties: AgentSecurityProperties)`.

- [ ] **Step 1: Write the failing properties tests**

Replace the body of `AgentSecurityPropertiesTests.kt` with:

```kotlin
package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentSecurityProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class AgentSecurityPropertiesTests {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AgentSecurityProperties::class)
    class PropertiesOnly

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(PropertiesOnly::class.java)

    private val complete = arrayOf(
        "app.security.required-scope=chat.mcp",
        "app.security.agents[0].client-id=client-a",
        "app.security.agents[0].username=agent-a",
        "app.security.agents[1].client-id=client-b",
        "app.security.agents[1].username=agent-b",
        "app.security.jwt.jwk-path=/tmp/agent-test.jwk",
    )

    private fun refusal(vararg values: String, message: String) {
        runner.withPropertyValues(*values).run { context ->
            assertThat(context.startupFailure).isNull()
            assertThatThrownBy { context.getBean(AgentSecurityProperties::class.java).requireComplete() }
                .hasMessageContaining(message)
        }
    }

    @Test
    fun `two agents and the scope bind`() {
        runner.withPropertyValues(*complete).run { context ->
            val complete = context.getBean(AgentSecurityProperties::class.java).requireComplete()
            assertThat(complete.agents.map { it.clientId }).containsExactly("client-a", "client-b")
            assertThat(complete.agents.map { it.username }).containsExactly("agent-a", "agent-b")
            assertThat(complete.requiredScope).isEqualTo("chat.mcp")
            assertThat(complete.requiredAuthority()).isEqualTo("SCOPE_chat.mcp")
            assertThat(complete.jwt.jwkPath).isEqualTo("/tmp/agent-test.jwk")
        }
    }

    @Test
    fun `the core may omit all agent values`() {
        runner.run { context ->
            assertThat(context.startupFailure).isNull()
            assertThat(context.getBean(AgentSecurityProperties::class.java).isConfigured()).isFalse()
        }
    }

    @Test
    fun `a service account list alone does not configure the agent path`() {
        runner.withPropertyValues("app.security.service-accounts=Service,Relay").run { context ->
            assertThat(context.getBean(AgentSecurityProperties::class.java).isConfigured()).isFalse()
        }
    }

    @Test
    fun `an absent scope names the property`() =
        refusal(*complete.filterNot { it.startsWith("app.security.required-scope") }.toTypedArray(),
            message = "app.security.required-scope is required.")

    @Test
    fun `an empty agent list names the property`() =
        refusal(*complete.filterNot { it.startsWith("app.security.agents") }.toTypedArray(),
            message = "app.security.agents requires at least one entry.")

    @Test
    fun `an entry with no client id names the index`() =
        refusal(*complete.filterNot { it.startsWith("app.security.agents[1].client-id") }.toTypedArray(),
            message = "app.security.agents[1].client-id is required.")

    @Test
    fun `an entry with no username names the index`() =
        refusal(*complete.filterNot { it.startsWith("app.security.agents[0].username") }.toTypedArray(),
            message = "app.security.agents[0].username is required.")

    @Test
    fun `an absent jwk path names the property`() =
        refusal(*complete.filterNot { it.startsWith("app.security.jwt.jwk-path") }.toTypedArray(),
            message = "app.security.jwt.jwk-path is required.")

    @Test
    fun `a shared client id fails and names it`() =
        refusal(*complete, "app.security.agents[1].client-id=client-a",
            message = "app.security.agents names client id 'client-a' twice.")

    @Test
    fun `a shared username fails and names it`() =
        refusal(*complete, "app.security.agents[1].username=agent-a",
            message = "app.security.agents names username 'agent-a' twice.")

    @Test
    fun `client ids compare exactly`() {
        runner.withPropertyValues(*complete, "app.security.agents[1].client-id=CLIENT-A").run { context ->
            assertThat(context.getBean(AgentSecurityProperties::class.java).requireComplete().agents)
                .hasSize(2)
        }
    }
}
```

- [ ] **Step 2: Write the failing converter tests**

In `AgentAuthenticationConverterTests.kt`, replace the `identity` field and `converter()` with two agents, and add the selection and list cases. Keep every other existing test, and change the client id in them from `client-under-test` to `client-a`:

```kotlin
    private val agentA = ChatUserDetails(
        User.create(Key.of(7L, 1L), "agent-a", "agent-a", "http://agent-a"), emptyList<String>(),
    )
    private val agentB = ChatUserDetails(
        User.create(Key.of(8L, 1L), "agent-b", "agent-b", "http://agent-b"), emptyList<String>(),
    )
    private val identities = AgentIdentities().apply {
        resolve(mapOf("client-a" to agentA, "client-b" to agentB))
    }

    private fun converter() = AgentAuthenticationConverter(identities)

    @Test
    fun `each client id selects its own agent`() {
        val a = converter().convert(jwt(mapOf("client_id" to "client-a", "scope" to "chat.mcp"))).block()!!
        val b = converter().convert(jwt(mapOf("client_id" to "client-b", "scope" to "chat.mcp"))).block()!!

        assertThat((a.principal as ChatUserDetails<*>).user.key).isEqualTo(Key.of(7L, 1L))
        assertThat((b.principal as ChatUserDetails<*>).user.key).isEqualTo(Key.of(8L, 1L))
    }

    @Test
    fun `a client id claim in a list raises the controlled failure`() {
        assertThrows<BadCredentialsException> {
            converter().convert(jwt(mapOf("client_id" to listOf("client-a"), "scope" to "chat.mcp"))).block()
        }
    }

    @Test
    fun `unresolved identities refuse to answer`() {
        val failure = assertThrows<IllegalStateException> { AgentIdentities().principalFor("client-a") }

        assertThat(failure.message).isEqualTo("The agent identities are not resolved.")
    }

    @Test
    fun `identities resolve once`() {
        val failure = assertThrows<IllegalStateException> { identities.resolve(emptyMap()) }

        assertThat(failure.message).isEqualTo("The agent identities were resolved more than once.")
    }
```

Change the import `com.demo.chat.config.agent.AgentIdentity` to `com.demo.chat.config.agent.AgentIdentities`. The `wrong client id` test keeps its `doesNotContain("another-client")` assertion.

- [ ] **Step 3: Write the failing lifecycle tests**

Replace `AgentIdentityLifecycleTests.kt` with:

```kotlin
package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentIdentities
import com.demo.chat.config.agent.AgentIdentityLifecycle
import com.demo.chat.config.agent.AgentSecurityProperties
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.composite.ChatUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import reactor.core.publisher.Flux

class AgentIdentityLifecycleTests {

    private fun agent(clientId: String, username: String) = AgentSecurityProperties.Agent().apply {
        this.clientId = clientId
        this.username = username
    }

    private fun properties(vararg agents: AgentSecurityProperties.Agent) = AgentSecurityProperties().apply {
        requiredScope = "chat.mcp"
        this.agents = agents.toList()
        jwt = AgentSecurityProperties.Jwt().apply { jwkPath = "/tmp/agent-test.jwk" }
    }

    private val twoAgents = properties(agent("client-a", "agent-a"), agent("client-b", "agent-b"))

    private fun user(id: Long, handle: String): User<Long> =
        User.create(Key.of(id, 1L), handle, handle, "http://$handle")

    private fun answer(users: ChatUserService<Long>, query: String, vararg found: User<Long>) {
        Mockito.`when`(users.findByUsername(ByStringRequest(query))).thenReturn(Flux.just(*found))
    }

    @Test
    fun `every configured agent resolves to its own principal`() {
        val users = mockUsers()
        answer(users, "agent-a", user(7L, "agent-a"))
        answer(users, "agent-b", user(8L, "agent-b"))
        val identities = AgentIdentities()

        AgentIdentityLifecycle(users, identities, twoAgents).start()

        assertThat(identities.principalFor("client-a")!!.user.key.id).isEqualTo(7L)
        assertThat(identities.principalFor("client-b")!!.user.key.id).isEqualTo(8L)
        assertThat(identities.principalFor("client-c")).isNull()
    }

    @Test
    fun `a missing second handle fails the start and names it`() {
        val users = mockUsers()
        answer(users, "agent-a", user(7L, "agent-a"))
        answer(users, "agent-b")

        val failure = assertThrows<IllegalStateException> {
            AgentIdentityLifecycle(users, AgentIdentities(), twoAgents).start()
        }

        assertThat(failure.message).isEqualTo(
            "The agent username 'agent-b' for client 'client-b' answered 0 users. It must answer exactly one user."
        )
    }

    @Test
    fun `a lookup that answers another case keeps zero users`() {
        val users = mockUsers()
        answer(users, "agent", user(7L, "Agent"))

        val failure = assertThrows<IllegalStateException> {
            AgentIdentityLifecycle(users, AgentIdentities(), properties(agent("client-a", "agent"))).start()
        }

        assertThat(failure.message).contains("'agent'").contains("answered 0 users")
    }

    @Test
    fun `a handle that answers twice fails the start`() {
        val users = mockUsers()
        answer(users, "agent-a", user(7L, "agent-a"), user(9L, "agent-a"))

        val failure = assertThrows<IllegalStateException> {
            AgentIdentityLifecycle(users, AgentIdentities(), properties(agent("client-a", "agent-a"))).start()
        }

        assertThat(failure.message).contains("answered 2 users")
    }

    @Test
    fun `two clients that resolve to one user key fail the start`() {
        val users = mockUsers()
        answer(users, "agent-a", user(7L, "agent-a"))
        answer(users, "agent-b", user(7L, "agent-b"))

        val failure = assertThrows<IllegalStateException> {
            AgentIdentityLifecycle(users, AgentIdentities(), twoAgents).start()
        }

        assertThat(failure.message).isEqualTo(
            "app.security.agents clients 'client-a' and 'client-b' resolve to one user key."
        )
    }

    @Test
    fun `an unconfigured core starts and resolves nothing`() {
        val lifecycle = AgentIdentityLifecycle(mockUsers(), AgentIdentities(), AgentSecurityProperties())

        lifecycle.start()

        assertThat(lifecycle.isRunning).isTrue()
    }

    @Test
    fun `the phase sits between the root keys and the web server`() {
        assertThat(AgentIdentityLifecycle.PHASE).isGreaterThan(Int.MAX_VALUE - 4096)
        assertThat(AgentIdentityLifecycle.PHASE).isLessThan(Int.MAX_VALUE - 2048)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mockUsers(): ChatUserService<Long> =
        Mockito.mock(ChatUserService::class.java) as ChatUserService<Long>
}
```

- [ ] **Step 4: Confirm that the tests fail**

Run:

```bash
LOG=$SCRATCH/t1-red.log; mvn -o -B -pl chat-security,chat-webflux -am test-compile > $LOG 2>&1; echo exit=$?; grep -E "ERROR.*\.kt" $LOG | head -5
```

Expected: exit 1. The errors name `AgentIdentities` and the `agents` property.

- [ ] **Step 5: Write `AgentIdentities.kt` and delete `AgentIdentity.kt`**

```kotlin
package com.demo.chat.config.agent

import com.demo.chat.security.ChatUserDetails

/**
 * Holds the chat identity of each agent client that this deployment accepts.
 * See `CHAT-frcrctdp`.
 *
 * The token `client_id` selects the identity. A client id that this map does
 * not hold is not an agent of this deployment.
 */
class AgentIdentities {

    @Volatile
    private var byClientId: Map<String, ChatUserDetails<*>>? = null

    /** Store the startup-resolved principal of each agent client. */
    fun resolve(byClientId: Map<String, ChatUserDetails<*>>) {
        check(this.byClientId == null) { "The agent identities were resolved more than once." }
        this.byClientId = byClientId.toMap()
    }

    /** Return the principal of one agent client, or null for an unknown client. */
    fun principalFor(clientId: String): ChatUserDetails<*>? =
        (byClientId ?: throw IllegalStateException("The agent identities are not resolved."))[clientId]
}
```

Run: `git rm chat-security/src/main/kotlin/com/demo/chat/config/agent/AgentIdentity.kt`

- [ ] **Step 6: Rewrite `AgentSecurityProperties.kt`**

```kotlin
package com.demo.chat.config.agent

import com.demo.chat.domain.ChatException
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The agent identities of this deployment. See `CHAT-pgpmsgvr` and
 * `CHAT-frcrctdp`.
 *
 * **No agent value has a default.** A default would make every deployment the
 * same agent in silence. That is the `app.nodeid` lesson.
 *
 * The core may omit this configuration. A REST application calls
 * [requireComplete] before it creates its resource server.
 */
@ConfigurationProperties("app.security")
class AgentSecurityProperties {

    /** The scope that every enforced request must carry. This deployment selects `chat.mcp`. */
    var requiredScope: String? = null

    /** One entry per agent. Each entry binds one OAuth client id to one chat user handle. */
    var agents: List<Agent> = emptyList()

    var jwt: Jwt? = null

    /**
     * The handles that hold `ROLE_SERVICE`. `UserDetailsConfiguration` reads the
     * same property with the same default. An agent must not name one of them.
     */
    var serviceAccounts: List<String> = listOf(DEFAULT_SERVICE_ACCOUNT)

    fun isConfigured(): Boolean = requiredScope != null || agents.isNotEmpty() || jwt != null

    fun requireComplete(): Complete {
        val scope = requiredScope?.takeIf { it.isNotBlank() }
            ?: throw ChatException("app.security.required-scope is required.")
        if (agents.isEmpty()) {
            throw ChatException("app.security.agents requires at least one entry.")
        }
        agents.forEachIndexed { index, agent -> agent.validate(index) }
        requireDistinct()
        val configuredJwt = jwt ?: throw ChatException("app.security.jwt.jwk-path is required.")
        configuredJwt.validate()
        return Complete(agents.toList(), scope, configuredJwt)
    }

    private fun requireDistinct() {
        agents.groupBy { it.clientId }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
            throw ChatException("app.security.agents names client id '$it' twice.")
        }
        agents.groupBy { it.username.lowercase(java.util.Locale.ROOT) }
            .filterValues { it.size > 1 }.values.firstOrNull()?.let {
                throw ChatException("app.security.agents names username '${it.last().username}' twice.")
            }
    }

    data class Complete(val agents: List<Agent>, val requiredScope: String, val jwt: Jwt) {
        /** The authority that the required scope gives. REST and the core both require it. */
        fun requiredAuthority(): String = authorityFor(requiredScope)
    }

    /** One OAuth client and the chat user that names its agent identity. */
    class Agent {
        /** The `client_id` claim that selects this agent. */
        lateinit var clientId: String
        /** The chat user handle that names this agent identity. */
        lateinit var username: String

        fun validate(index: Int) {
            if (!::clientId.isInitialized || clientId.isBlank()) {
                throw ChatException("app.security.agents[$index].client-id is required.")
            }
            if (!::username.isInitialized || username.isBlank()) {
                throw ChatException("app.security.agents[$index].username is required.")
            }
        }
    }

    /** The trusted signing material. */
    class Jwt {
        /** The JWK file that holds the trusted public key. */
        lateinit var jwkPath: String

        fun validate() {
            if (!::jwkPath.isInitialized || jwkPath.isBlank()) {
                throw ChatException("app.security.jwt.jwk-path is required.")
            }
        }
    }

    companion object {
        /** The prefix that Spring Security gives a scope authority. */
        const val SCOPE_PREFIX = "SCOPE_"

        /** The service account that `userinit.yml` declares. */
        const val DEFAULT_SERVICE_ACCOUNT = "Service"

        fun authorityFor(scope: String): String = "$SCOPE_PREFIX$scope"
    }
}
```

- [ ] **Step 7: Rewrite the converter**

```kotlin
package com.demo.chat.config.agent

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import reactor.core.publisher.Mono

/**
 * Converts a validated JWT into the authentication of the agent that its
 * `client_id` selects. See `CHAT-frcrctdp`.
 *
 * REST and the core use this class. Each side builds its own instance from its
 * own agent list.
 */
class AgentAuthenticationConverter(
    private val identities: AgentIdentities,
) : Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private val authoritiesConverter = JwtGrantedAuthoritiesConverter()

    override fun convert(jwt: Jwt): Mono<AbstractAuthenticationToken> {
        val clientId = jwt.claims["client_id"] as? String
        val principal = clientId?.let { identities.principalFor(it) }
            ?: return Mono.error(
                BadCredentialsException("The token is not from a configured agent client.")
            )

        return Mono.just(
            AgentAuthenticationToken(principal, jwt, authoritiesConverter.convert(jwt).orEmpty())
        )
    }
}
```

- [ ] **Step 8: Rewrite the lifecycle**

```kotlin
package com.demo.chat.config.agent

import com.demo.chat.domain.ByStringRequest
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.composite.ChatUserService
import org.springframework.context.SmartLifecycle

/**
 * Resolves every configured agent before the web server starts. See
 * `CHAT-frcrctdp`.
 *
 * **The handle match is exact.** The Lucene user index analyzes the handle and
 * lowercases it, so a lookup for `admin` answers the user `Admin`. This class
 * keeps only a user whose handle equals the configured handle.
 */
class AgentIdentityLifecycle<T>(
    private val users: ChatUserService<T>,
    private val identities: AgentIdentities,
    private val properties: AgentSecurityProperties,
) : SmartLifecycle {

    @Volatile
    private var running = false

    override fun start() {
        if (!properties.isConfigured()) {
            running = true
            return
        }
        val resolved = linkedMapOf<String, ChatUserDetails<*>>()
        properties.requireComplete().agents.forEach { agent ->
            val matches = users.findByUsername(ByStringRequest(agent.username))
                .collectList().block().orEmpty()
                .filter { it.handle == agent.username }
            if (matches.size != 1) {
                throw IllegalStateException(
                    "The agent username '${agent.username}' for client '${agent.clientId}' " +
                        "answered ${matches.size} users. It must answer exactly one user."
                )
            }
            resolved[agent.clientId] = ChatUserDetails(matches.single(), listOf("ROLE_AGENT"))
        }
        requireOneClientPerUser(resolved)
        identities.resolve(resolved)
        running = true
    }

    private fun requireOneClientPerUser(resolved: Map<String, ChatUserDetails<*>>) {
        resolved.entries.groupBy { it.value.user.key }.values.firstOrNull { it.size > 1 }?.let {
            throw IllegalStateException(
                "app.security.agents clients '${it[0].key}' and '${it[1].key}' resolve to one user key."
            )
        }
    }

    override fun stop() {
        running = false
    }

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = PHASE

    companion object {
        /** Runs after root loading and before the reactive web server. */
        const val PHASE = Int.MAX_VALUE - 3072
    }
}
```

- [ ] **Step 9: Update the REST wiring**

In `AgentSecurityConfiguration.kt`, replace the `agentAuthenticationConverter`, `agentIdentity` and `agentIdentityLifecycle` beans:

```kotlin
    @Bean
    fun agentAuthenticationConverter(
        identities: AgentIdentities,
    ): Converter<Jwt, Mono<AbstractAuthenticationToken>> = AgentAuthenticationConverter(identities)

    @Bean
    fun agentIdentities(): AgentIdentities = AgentIdentities()

    @Bean
    fun <T> agentIdentityLifecycle(
        services: CompositeServiceBeans<T, String>,
        identities: AgentIdentities,
        properties: AgentSecurityProperties,
    ): AgentIdentityLifecycle<T> = AgentIdentityLifecycle(services.userService(), identities, properties)
```

In `AgentResourceServerChain.kt:19`, change the body to:

```kotlin
    fun requiredAuthority(): String = properties.requireComplete().requiredAuthority()
```

- [ ] **Step 10: Update the core wiring**

In `RSocketAgentSecurityConfiguration.kt`:

- Replace `fun agentIdentity(): AgentIdentity = AgentIdentity()` with `fun agentIdentities(): AgentIdentities = AgentIdentities()`.
- Delete `@ConditionalOnProperty("app.security.agent.username")` on `agentIdentityLifecycle`. The lifecycle now does nothing when the core holds no agent value.
- Change the lifecycle parameter `identity: AgentIdentity` to `identities: AgentIdentities` and pass it.
- In `rsocketAuthenticationManager`, change the parameter `identity: AgentIdentity` to `identities: AgentIdentities`, and replace the `else` branch with:

```kotlin
        } else {
            val jwtDecoder = decoder.getIfAvailable {
                throw IllegalStateException("The configured agent bearer path has no JWT decoder.")
            }
            val complete = properties.requireComplete()
            RequiredScopeAuthenticationManager(
                JwtReactiveAuthenticationManager(jwtDecoder).apply {
                    setJwtAuthenticationConverter(AgentAuthenticationConverter(identities))
                },
                complete.requiredAuthority(),
            )
        }
```

- Fix the imports: `AgentIdentity` becomes `AgentIdentities`.

- [ ] **Step 11: Update the test fixtures**

`DeployTestSigningKey.register` becomes:

```kotlin
    fun register(registry: DynamicPropertyRegistry) {
        registry.add("app.security.jwt.jwk-path") { jwkPath }
        registry.add("app.security.agents[0].client-id") { "client-under-test" }
        registry.add("app.security.agents[0].username") { "agent-svc" }
        registry.add("app.security.required-scope") { "chat.mcp" }
    }
```

In `BothChainsApplication.kt` and `DeniedCallerApplication.kt`, replace the `agentIdentity` bean and the converter bean:

```kotlin
    @Bean
    fun agentIdentities(): AgentIdentities = AgentIdentities().apply {
        resolve(
            mapOf(
                "client-under-test" to ChatUserDetails(
                    User.create(Key.of(7L, 1L), "agent-svc", "agent-svc", "http://agent-svc"),
                    emptyList<String>(),
                )
            )
        )
    }

    @Bean
    fun agentAuthenticationConverter(
        identities: AgentIdentities,
    ): Converter<Jwt, Mono<AbstractAuthenticationToken>> = AgentAuthenticationConverter(identities)
```

In `WebFluxAnonymousIdentityTests.agentChain()` and in `AgentResourceServerChainTests`, build properties with the new shape:

```kotlin
        val properties = AgentSecurityProperties().apply {
            requiredScope = "chat.mcp"
            agents = listOf(AgentSecurityProperties.Agent().apply {
                clientId = "client-under-test"
                username = "agent-svc"
            })
            jwt = AgentSecurityProperties.Jwt().apply { jwkPath = WebFluxTestSigningKey.path() }
        }
        val identities = AgentIdentities().apply {
            resolve(mapOf("client-under-test" to ChatUserDetails(
                User.create(TestKeys.key(2L), "agent-svc", "agent-svc", "http://agent-svc"),
                emptyList<String>(),
            )))
        }
```

Pass `AgentAuthenticationConverter(identities)` where the old code passed `AgentAuthenticationConverter(identity, "client-under-test")`. Read `AgentResourceServerChainTests.kt` first, and apply the same property shape wherever it sets `agent = ...`.

In `CoreBearerDenialNoDownstreamEffectsTests.kt:58-61`, replace the three `app.security.agent.*` lines with:

```kotlin
        "app.security.required-scope=chat.mcp",
        "app.security.agents[0].client-id=client-under-test",
        "app.security.agents[0].username=Agent",
```

- [ ] **Step 12: Find any caller that this task missed**

Run:

```bash
grep -rnE "AgentIdentity\b|AgentIdentity\(|app\.security\.agent\.|\.agent\.(clientId|username|requiredScope)" --include='*.kt' . | grep -v /target/ | grep -v .worktrees
```

Expected: no line. Fix each line that appears, with the shapes above.

- [ ] **Step 13: Run the unit tests**

```bash
LOG=$SCRATCH/t1-green.log; mvn -o -B -pl chat-security,chat-webflux,chat-service-controller,chat-deploy -am test \
  -Dtest='AgentSecurityPropertiesTests,AgentAuthenticationConverterTests,AgentIdentityLifecycleTests,AgentIdentityResolutionTests,AgentResourceServerChainTests,WebFluxAnonymousIdentityTests,SecurityChain*,Denied*' \
  -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:.*Fail|<<< FAIL" $LOG | tail -8
```

Expected: exit 0, and no `<<< FAIL` line.

- [ ] **Step 14: Run the core denial test in the full reactor**

The register warns that a scoped `-pl` run reads stale jars from `~/.m2`. This test boots `ChatApp`, so run it with `-am`:

```bash
LOG=$SCRATCH/t1-core.log; mvn -o -B -pl chat-deploy-memory -am test -Dtest=CoreBearerDenialNoDownstreamEffectsTests \
  -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|<<< FAIL" $LOG | tail -3
```

Expected: exit 0. The `wrong-client` case still answers `0x401`. That case is the unlisted-client case of the spec.

- [ ] **Step 15: Commit**

```bash
git add -A chat-security chat-webflux chat-service-controller chat-deploy chat-deploy-memory
git commit -F - <<'EOF'
Select the agent identity from the token client id (CHAT-frcrctdp)

app.security.agents replaces the single agent. AgentIdentities maps each
client id to its principal, and the converter looks the client id up. The
lifecycle keeps an exact handle match and refuses two clients that resolve
to one user key.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 2: Reserved handles, case rules, and the old key guard

**Files:**
- Modify: `chat-security/src/main/kotlin/com/demo/chat/config/agent/AgentSecurityProperties.kt`
- Create: `chat-security/src/main/kotlin/com/demo/chat/config/agent/AgentSecurityPropertiesGuard.kt`
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentSecurityConfiguration.kt` (`agentResourceServerChain`)
- Modify: `chat-service-controller/src/main/kotlin/com/demo/chat/config/rsocket/RSocketAgentSecurityConfiguration.kt` (`validateAgentSecurityProperties`, `AgentSecurityPropertiesValidator`)
- Test: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/AgentSecurityPropertiesTests.kt`
- Test: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/AgentSecurityPropertiesGuardTests.kt`

**Interfaces:**
- Consumes: `AgentSecurityProperties` from Task 1.
- Produces: `AgentSecurityPropertiesGuard.requireNoLegacyKeys(environment: ConfigurableEnvironment)`.

- [ ] **Step 1: Write the failing reserved and case tests**

Add to `AgentSecurityPropertiesTests`:

```kotlin
    @ParameterizedTest
    @ValueSource(strings = ["Admin", "admin", "ANON", "Anon", "service", "Service"])
    fun `a reserved username fails and names it`(name: String) =
        refusal(*complete, "app.security.agents[1].username=$name",
            message = "app.security.agents names reserved username '$name'. An agent must be a plain user.")

    @Test
    fun `a configured service account is reserved`() =
        refusal(*complete, "app.security.service-accounts=Service,Relay", "app.security.agents[1].username=relay",
            message = "app.security.agents names reserved username 'relay'. An agent must be a plain user.")

    @Test
    fun `usernames that differ by case alone are one username`() =
        refusal(*complete, "app.security.agents[0].username=Claude", "app.security.agents[1].username=claude",
            message = "app.security.agents names username 'claude' twice.")
```

Add the imports `org.junit.jupiter.params.ParameterizedTest` and `org.junit.jupiter.params.provider.ValueSource`.

- [ ] **Step 2: Write the failing guard tests**

Create `AgentSecurityPropertiesGuardTests.kt`:

```kotlin
package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentSecurityPropertiesGuard
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource

class AgentSecurityPropertiesGuardTests {

    private fun environment(vararg values: Pair<String, Any>) = StandardEnvironment().apply {
        propertySources.addFirst(MapPropertySource("test", mapOf(*values)))
    }

    @ParameterizedTest
    @ValueSource(strings = ["app.security.agent.client-id", "app.security.agent.username", "app.security.agent.required-scope", "app.security.agent.clientId"])
    fun `an old key fails the start`(key: String) {
        assertThatThrownBy { AgentSecurityPropertiesGuard.requireNoLegacyKeys(environment(key to "x")) }
            .hasMessage(
                "app.security.agent.* is replaced by app.security.agents[n] and app.security.required-scope. See CHAT-frcrctdp."
            )
    }

    @Test
    fun `an environment variable form of an old key fails the start`() {
        val env = StandardEnvironment().apply {
            propertySources.addFirst(
                SystemEnvironmentPropertySource("systemEnvironment", mapOf("APP_SECURITY_AGENT_CLIENTID" to "x"))
            )
        }

        assertThatThrownBy { AgentSecurityPropertiesGuard.requireNoLegacyKeys(env) }
            .hasMessageContaining("app.security.agent.* is replaced")
    }

    @Test
    fun `the new keys pass`() {
        assertThatCode {
            AgentSecurityPropertiesGuard.requireNoLegacyKeys(
                environment(
                    "app.security.agents[0].client-id" to "a",
                    "app.security.agents[0].username" to "b",
                    "app.security.required-scope" to "chat.mcp",
                )
            )
        }.doesNotThrowAnyException()
    }
}
```

Note: `StandardEnvironment` already holds the real system environment. Run the test on a machine with no `APP_SECURITY_AGENT_*` variable set.

- [ ] **Step 3: Confirm that the tests fail**

The case test `usernames that differ by case alone are one username` passes already, because Task 1 compares usernames without case. The reserved tests and the guard tests fail.

```bash
LOG=$SCRATCH/t2-red.log; mvn -o -B -pl chat-security,chat-webflux -am test -Dtest='AgentSecurityProperties*' \
  -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "ERROR.*\.kt|<<< FAIL" $LOG | head -5
```

Expected: exit 1. The guard class does not exist.

- [ ] **Step 4: Add the reserved and case rules**

In `AgentSecurityProperties`, call `requireNoReserved()` after `requireDistinct()` in `requireComplete()`, and add:

```kotlin
    /**
     * An agent must be a plain user. See `CHAT-frcrctdp`.
     *
     * **The comparison ignores case.** The Lucene user index lowercases the
     * handle, so a lookup for `admin` answers the user `Admin`.
     */
    private fun requireNoReserved() {
        val reserved = (RESERVED_IDENTITIES + serviceAccounts.filter { it.isNotBlank() })
            .map { it.lowercase(java.util.Locale.ROOT) }
            .toSet()
        agents.firstOrNull { it.username.lowercase(java.util.Locale.ROOT) in reserved }?.let {
            throw ChatException(
                "app.security.agents names reserved username '${it.username}'. An agent must be a plain user."
            )
        }
    }
```

In the companion object, add:

```kotlin
        /** The two `ChatIdentity` names. Neither one is a plain user. */
        val RESERVED_IDENTITIES = listOf("Admin", "Anon")
```

- [ ] **Step 5: Write the guard**

```kotlin
package com.demo.chat.config.agent

import com.demo.chat.domain.ChatException
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource
import org.springframework.core.env.ConfigurableEnvironment

/**
 * Refuses the single agent keys that `CHAT-frcrctdp` replaced.
 *
 * **Spring ignores an unbound key.** Without this guard, a launch with the old
 * keys would start with no agent and refuse every token.
 */
object AgentSecurityPropertiesGuard {

    private val LEGACY = ConfigurationPropertyName.of("app.security.agent")

    fun requireNoLegacyKeys(environment: ConfigurableEnvironment) {
        val present = ConfigurationPropertySources.get(environment)
            .filterIsInstance<IterableConfigurationPropertySource>()
            .any { source -> source.any { LEGACY.isAncestorOf(it) } }
        if (present) {
            throw ChatException(
                "app.security.agent.* is replaced by app.security.agents[n] and app.security.required-scope. " +
                    "See CHAT-frcrctdp."
            )
        }
    }
}
```

- [ ] **Step 6: Call the guard on both sides**

In `AgentSecurityConfiguration.agentResourceServerChain`, change the parameter `environment: Environment` to `environment: ConfigurableEnvironment` (import `org.springframework.core.env.ConfigurableEnvironment`). Call `AgentSecurityPropertiesGuard.requireNoLegacyKeys(environment)` as the first line.

In `RSocketAgentSecurityConfiguration`:

```kotlin
    @Bean
    fun validateAgentSecurityProperties(
        properties: AgentSecurityProperties,
        environment: ConfigurableEnvironment,
    ) = AgentSecurityPropertiesValidator(properties, environment)
```

```kotlin
class AgentSecurityPropertiesValidator(properties: AgentSecurityProperties, environment: ConfigurableEnvironment) {

    init {
        AgentSecurityPropertiesGuard.requireNoLegacyKeys(environment)
        if (properties.isConfigured()) {
            properties.requireComplete()
        }
    }
}
```

- [ ] **Step 7: Run the tests**

```bash
LOG=$SCRATCH/t2-green.log; mvn -o -B -pl chat-security,chat-webflux -am test -Dtest='AgentSecurityProperties*' \
  -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|<<< FAIL" $LOG | tail -3
```

Expected: exit 0.

- [ ] **Step 8: Prove the reserved rule with two mutations**

Mutation A: in `requireNoReserved`, remove both `.lowercase(java.util.Locale.ROOT)` calls. Run the Step 7 command. Expected: the `admin`, `ANON` and `service` cases fail. Restore the file by absolute path.

Mutation B: comment out the `requireNoReserved()` call. Run the Step 7 command. Expected: every reserved case fails. Restore the file by absolute path.

```bash
git status --short chat-security
```

Expected: only the files of this task, with no mutation left.

- [ ] **Step 9: Commit**

```bash
git add -A chat-security chat-webflux chat-service-controller
git commit -F - <<'EOF'
Refuse reserved agent handles and the old agent keys (CHAT-frcrctdp)

Admin, Anon and every service account are reserved. Handle checks ignore
case, because the Lucene handle field is lowercased. A launch that sets any
app.security.agent.* key fails the start and names the new keys.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 3: Measure REST selection and core selection apart

**Files:**
- Create: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/AgentSecurityConfigurationWiringTests.kt`
- Create: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/CoreAgentSelectionTests.kt`

**Interfaces:**
- Consumes: the beans `agentAuthenticationConverter` and `agentIdentities` of `AgentSecurityConfiguration`.
- Consumes: `CompositeServiceBeans<T, String>.userService()` and `.topicService()`, `PersistenceServiceBeans<Long, String>.authMetaPersistence()`.

**Why two tests.** On the relay path, REST forwards the bearer token and the core selects the identity again. So a broken REST selection passes a relay owner row test. The REST test reads the production REST wiring. The core test reads the production core wiring.

- [ ] **Step 1: Write the REST wiring test**

```kotlin
package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.agent.AgentSecurityConfiguration
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.composite.ChatUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.Jwt
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

/**
 * The production REST wiring selects the agent of each token. See
 * `CHAT-frcrctdp`.
 *
 * This test reads the converter bean that `AgentSecurityConfiguration` builds.
 * A mutation of that wiring must fail here, and the relay test cannot see it.
 */
class AgentSecurityConfigurationWiringTests {

    @Suppress("UNCHECKED_CAST")
    private fun services(): CompositeServiceBeans<Long, String> {
        val users = Mockito.mock(ChatUserService::class.java) as ChatUserService<Long>
        Mockito.`when`(users.findByUsername(ByStringRequest("agent-a")))
            .thenReturn(Flux.just(User.create(Key.of(7L, 1L), "agent-a", "agent-a", "http://a")))
        Mockito.`when`(users.findByUsername(ByStringRequest("agent-b")))
            .thenReturn(Flux.just(User.create(Key.of(8L, 1L), "agent-b", "agent-b", "http://b")))
        val services = Mockito.mock(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>
        Mockito.`when`(services.userService()).thenReturn(users)
        return services
    }

    private val runner = ReactiveWebApplicationContextRunner()
        .withUserConfiguration(AgentSecurityConfiguration::class.java)
        .withBean(CompositeServiceBeans::class.java, { services() })
        .withPropertyValues(
            "app.primary=REST",
            "app.security.required-scope=chat.mcp",
            "app.security.agents[0].client-id=client-a",
            "app.security.agents[0].username=agent-a",
            "app.security.agents[1].client-id=client-b",
            "app.security.agents[1].username=agent-b",
            "app.security.jwt.jwk-path=${WebFluxTestSigningKey.path()}",
        )

    private fun jwt(clientId: String) = Jwt(
        "value", Instant.now(), Instant.now().plusSeconds(60), mapOf("alg" to "ES256"),
        mapOf("client_id" to clientId, "scope" to "chat.mcp"),
    )

    @Test
    fun `the REST converter selects the agent of each client`() {
        runner.run { context ->
            assertThat(context.startupFailure).isNull()
            @Suppress("UNCHECKED_CAST")
            val converter = context.getBean("agentAuthenticationConverter")
                as Converter<Jwt, Mono<AbstractAuthenticationToken>>

            val a = converter.convert(jwt("client-a"))!!.block()!!
            val b = converter.convert(jwt("client-b"))!!.block()!!

            assertThat((a.principal as ChatUserDetails<*>).user.key).isEqualTo(Key.of(7L, 1L))
            assertThat((b.principal as ChatUserDetails<*>).user.key).isEqualTo(Key.of(8L, 1L))
        }
    }

    @Test
    fun `the REST converter refuses an unlisted client`() {
        runner.run { context ->
            @Suppress("UNCHECKED_CAST")
            val converter = context.getBean("agentAuthenticationConverter")
                as Converter<Jwt, Mono<AbstractAuthenticationToken>>

            StepVerifier.create(converter.convert(jwt("client-unlisted"))!!)
                .expectError(BadCredentialsException::class.java)
                .verify()
        }
    }

    @Test
    fun `an old key fails the REST start`() {
        runner.withPropertyValues("app.security.agent.client-id=client-a").run { context ->
            assertThat(context.startupFailure).hasRootCauseMessage(
                "app.security.agent.* is replaced by app.security.agents[n] and app.security.required-scope. See CHAT-frcrctdp."
            )
        }
    }
}
```

If the context fails to start for a bean that `AgentSecurityConfiguration` needs and the test does not supply, read the failure. Supply that bean with `withBean`, as the test supplies `CompositeServiceBeans`. Do not add `@SpringBootTest`.

- [ ] **Step 2: Run the REST wiring test**

```bash
LOG=$SCRATCH/t3-rest.log; mvn -o -B -pl chat-webflux -am test -Dtest=AgentSecurityConfigurationWiringTests \
  -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|<<< FAIL" $LOG | tail -3
```

Expected: exit 0, 3 tests.

- [ ] **Step 3: Write the core selection test**

```kotlin
package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.security.rsocket.RSocketSecurityErrorCodes
import io.rsocket.exceptions.CustomRSocketException
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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.util.MimeTypeUtils
import reactor.test.StepVerifier
import java.time.Duration

/**
 * The core selects the agent of each bearer token. See `CHAT-frcrctdp`.
 *
 * Two agents each add a room over RSocket. The owner row of each room names
 * the key of the agent whose token added it. The second agent user comes from
 * the bracket form of `app.init.initial-users`, as `chat-build --agent` emits it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-core-agent-selection",
        "app.server.proto=rsocket", "server.port=0", "spring.rsocket.server.port=0",
        "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory", "app.service.core.pubsub=memory",
        "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite=true",
        "app.service.composite.auth=true",
        "app.controller.user=true", "app.controller.topic=true", "app.controller.message=true",
        "app.service.security.userdetails=true",
        "app.users.create=true",
        "app.init.initial-users[Claude].handle=Claude",
        "app.init.initial-users[Claude].name=Claude",
        "app.init.initial-users[Claude].image-uri=chatimg://agent.png",
        "app.security.required-scope=chat.mcp",
        "app.security.agents[0].client-id=client-agent",
        "app.security.agents[0].username=Agent",
        "app.security.agents[1].client-id=client-claude",
        "app.security.agents[1].username=Claude",
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoreAgentSelectionTests {

    @Autowired lateinit var strategies: RSocketStrategies
    @Autowired lateinit var composite: CompositeServiceBeans<Long, String>
    @Autowired lateinit var stores: PersistenceServiceBeans<Long, String>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val timeout = Duration.ofSeconds(10)

    @Test
    fun `each agent token writes an owner row that names its own agent`() {
        addRoom("client-agent", "agentroom")
        addRoom("client-claude", "clauderoom")

        assertThat(ownerOf("agentroom")).isEqualTo(keyOf("Agent"))
        assertThat(ownerOf("clauderoom")).isEqualTo(keyOf("Claude"))
        assertThat(keyOf("Agent")).isNotEqualTo(keyOf("Claude"))
    }

    @Test
    fun `an unlisted client is refused as authentication`() {
        val requester = bearerRequester()
        try {
            StepVerifier.create(
                requester.route("topic.topic-add")
                    .metadata(BearerTokenMetadata(token("client-unlisted")), BEARER)
                    .data(ByStringRequest("unlistedroom"))
                    .retrieveMono(Map::class.java)
            ).expectErrorSatisfies { error ->
                assertThat((error as CustomRSocketException).errorCode())
                    .isEqualTo(RSocketSecurityErrorCodes.AUTHENTICATION)
            }.verify(timeout)
        } finally {
            requester.dispose()
        }
    }

    private fun addRoom(clientId: String, name: String) {
        val requester = bearerRequester()
        try {
            StepVerifier.create(
                requester.route("topic.topic-add")
                    .metadata(BearerTokenMetadata(token(clientId)), BEARER)
                    .data(ByStringRequest(name))
                    .retrieveMono(Map::class.java)
            ).expectNextCount(1).verifyComplete()
        } finally {
            requester.dispose()
        }
    }

    private fun ownerOf(room: String): Key<Long> {
        val roomKey = composite.topicService().getRoomByName(ByStringRequest(room)).block(timeout)!!.key
        val owners = stores.authMetaPersistence().all().collectList().block(timeout)!!
            .filter { it.target.id == roomKey.id && it.permission == "*" && !it.mute }
        assertThat(owners).describedAs("the owner rows of $room").hasSize(1)
        return owners.single().principal
    }

    private fun keyOf(handle: String): Key<Long> =
        composite.userService().findByUsername(ByStringRequest(handle))
            .filter { it.handle == handle }.single().block(timeout)!!.key

    private fun token(clientId: String) = CoreAgentTokens.mint(signingKeyPath, clientId)

    private fun bearerRequester(): RSocketRequester = RSocketRequester.builder()
        .rsocketStrategies(strategies.mutate().encoders { it.add(0, BearerTokenAuthenticationEncoder()) }.build())
        .connectTcp("localhost", port)
        .block(timeout)!!

    companion object {
        private val BEARER = MimeTypeUtils.parseMimeType("message/x.rsocket.authentication.v0")
        private val signingKeyPath = CoreAgentTokens.createKey()

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.security.jwt.jwk-path") { signingKeyPath }
        }
    }
}

private object CoreAgentTokens {

    fun createKey(): String {
        val key = com.nimbusds.jose.jwk.gen.ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256)
            .keyID("core-agent-selection").generate()
        val file = java.nio.file.Files.createTempFile("core-agent-selection", ".jwk")
        file.toFile().deleteOnExit()
        java.nio.file.Files.writeString(file, key.toJSONString())
        return file.toString()
    }

    fun mint(path: String, clientId: String): String {
        val key = com.nimbusds.jose.jwk.JWK.parse(java.nio.file.Files.readString(java.nio.file.Paths.get(path)))
            as com.nimbusds.jose.jwk.ECKey
        val claims = com.nimbusds.jwt.JWTClaimsSet.Builder()
            .issuer("https://authserv").subject(clientId)
            .claim("client_id", clientId).claim("scope", "chat.mcp")
            .issueTime(java.util.Date(System.currentTimeMillis() - 1_000))
            .expirationTime(java.util.Date(System.currentTimeMillis() + 60_000))
            .build()
        val token = com.nimbusds.jwt.SignedJWT(
            com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.ES256).keyID(key.keyID).build(),
            claims,
        )
        token.sign(com.nimbusds.jose.crypto.ECDSASigner(key))
        return token.serialize()
    }
}
```

Check two names before you run it. `CompositeServiceBeans` must declare `topicService()`, and `ChatTopicService` must declare `getRoomByName`. Read `chat-core/src/main/kotlin/com/demo/chat/config/CompositeServiceBeans.kt`. Use the name that the file declares.

- [ ] **Step 4: Run the core selection test**

```bash
LOG=$SCRATCH/t3-core.log; mvn -o -B -pl chat-deploy-memory -am test -Dtest=CoreAgentSelectionTests \
  -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|<<< FAIL|Caused by" $LOG | tail -5
```

Expected: exit 0, 2 tests.

- [ ] **Step 5: Mutate the REST wiring**

In `AgentSecurityConfiguration.agentAuthenticationConverter`, replace the body with a converter that sends every token to the first agent:

```kotlin
    @Bean
    fun agentAuthenticationConverter(
        identities: AgentIdentities,
        properties: AgentSecurityProperties,
    ): Converter<Jwt, Mono<AbstractAuthenticationToken>> {
        val first = properties.requireComplete().agents.first().clientId
        val delegate = AgentAuthenticationConverter(identities)
        return Converter { jwt ->
            delegate.convert(
                Jwt.withTokenValue(jwt.tokenValue).headers { it.putAll(jwt.headers) }
                    .claims { it.putAll(jwt.claims); it["client_id"] = first }.build()
            )
        }
    }
```

Run the Step 2 command. Expected: `the REST converter selects the agent of each client` fails. Run the Step 4 command. Expected: exit 0, because the core wiring is not touched. Restore the file by absolute path.

- [ ] **Step 6: Mutate the core wiring**

In `RSocketAgentSecurityConfiguration.rsocketAuthenticationManager`, apply the same first-agent wrapper to the converter that `setJwtAuthenticationConverter` takes, with `complete.agents.first().clientId`. Run the Step 4 command. Expected: `each agent token writes an owner row that names its own agent` fails. Run the Step 2 command. Expected: exit 0. Restore the file by absolute path.

```bash
git status --short chat-webflux chat-service-controller
```

Expected: only the two new test files are untracked. No main file is modified.

- [ ] **Step 7: Commit**

```bash
git add chat-webflux/src/test chat-deploy-memory/src/test
git commit -F - <<'EOF'
Measure REST and core agent selection apart (CHAT-frcrctdp)

The REST test reads the converter that AgentSecurityConfiguration builds.
The core test writes one room per agent over RSocket and reads each owner
row. A first-agent mutation of each wiring fails its own test alone.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 4: `chat-build --agent`, goldens, and binding proofs

**Files:**
- Modify: `shell-scripts/chat-build` (lines near 518-520, 662-672, 987-992, 1065-1081)
- Modify: `shell-scripts/test-flags.sh` (CASES and a new REFUSALS block)
- Modify: `shell-scripts/golden/core-client-agent.flags`, `shell-scripts/golden/rest-client-agent.flags`
- Create: `shell-scripts/golden/authserv-client-agent.flags`
- Modify: `chat-deploy/src/test/kotlin/com/demo/chat/test/deploy/init/UserInitConfigBindingTests.kt`
- Modify: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/AgentSecurityPropertiesTests.kt`

**Interfaces:**
- Produces: `chat-build <core|rest|authserv> --agent CLIENT_ID=HANDLE [--agent ...] [--agent-scope SCOPE]`.

- [ ] **Step 1: Write the failing binding tests**

Add to `UserInitConfigBindingTests`:

```kotlin
    /**
     * `chat-build --agent` emits each agent user in the bracket form. See
     * `CHAT-frcrctdp`.
     *
     * **Why the brackets.** Without brackets, relaxed binding can select the
     * same handle value for both entries. Brackets preserve distinct handle
     * values.
     */
    @Test
    fun `bracketed launch users join the shipped users and keep their own handles`() {
        val sources = propertySources().apply {
            addFirst(
                MapPropertySource(
                    "launch",
                    mapOf(
                        "app.init.initial-users[Bot_1].handle" to "Bot_1",
                        "app.init.initial-users[Bot_1].name" to "Bot_1",
                        "app.init.initial-users[Bot_1].image-uri" to "chatimg://agent.png",
                        "app.init.initial-users[Bot1].handle" to "Bot1",
                        "app.init.initial-users[Bot1].name" to "Bot1",
                        "app.init.initial-users[Bot1].image-uri" to "chatimg://agent.png",
                    )
                )
            )
        }

        val bound = Binder(ConfigurationPropertySources.from(sources))
            .bind(PREFIX, UserInitializationProperties::class.java).get()

        assertThat(bound.initialUsers.keys).contains("Admin", "Anon", "Agent", "Service", "Bot_1", "Bot1")
        assertThat(bound.initialUsers.getValue("Bot_1").handle).isEqualTo("Bot_1")
        assertThat(bound.initialUsers.getValue("Bot1").handle).isEqualTo("Bot1")
        assertThat(bound.initialUsers.getValue("Agent").name).isEqualTo("MCP ADAPTER")
    }
```

Add the import `org.springframework.core.env.MapPropertySource`.

Add to `AgentSecurityPropertiesTests`:

```kotlin
    @Test
    fun `a higher source replaces the whole agent list`() {
        val sources = org.springframework.core.env.MutablePropertySources().apply {
            addLast(org.springframework.core.env.MapPropertySource("launch", mapOf(
                "app.security.agents[0].client-id" to "client-launch",
                "app.security.agents[0].username" to "launch",
            )))
            addLast(org.springframework.core.env.MapPropertySource("file", mapOf(
                "app.security.agents[0].client-id" to "client-file-a",
                "app.security.agents[0].username" to "file-a",
                "app.security.agents[1].client-id" to "client-file-b",
                "app.security.agents[1].username" to "file-b",
            )))
        }

        val bound = org.springframework.boot.context.properties.bind.Binder(
            org.springframework.boot.context.properties.source.ConfigurationPropertySources.from(sources)
        ).bind("app.security", AgentSecurityProperties::class.java).get()

        assertThat(bound.agents.map { it.clientId }).containsExactly("client-launch")
    }
```

- [ ] **Step 2: Run the binding tests**

```bash
LOG=$SCRATCH/t4-bind.log; mvn -o -B -pl chat-deploy,chat-webflux -am test -Dtest='UserInitConfigBindingTests,AgentSecurityPropertiesTests' \
  -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|<<< FAIL" $LOG | tail -4
```

Expected: exit 0. These tests prove Spring behaviour that the design relies on. They are not red first, because no product code changes here. Then remove the brackets from the `Bot_1` keys in the test, run again, and expect the `Bot_1` test to fail. Restore the test by absolute path.

- [ ] **Step 3: Write the failing golden cases**

In `test-flags.sh`, replace the two agent cases and add one:

```bash
  "core-client-agent|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent 31649af5-0154-4be5-8695-fda9d18b7981=Agent --agent 7c1e0b2a-5d0e-4c55-9d1e-2f6a8b3c4d5e=Claude"
```

```bash
  "rest-client-agent|rest --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent 31649af5-0154-4be5-8695-fda9d18b7981=Agent --agent 7c1e0b2a-5d0e-4c55-9d1e-2f6a8b3c4d5e=Claude"
```

```bash
  "authserv-client-agent|authserv --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent 31649af5-0154-4be5-8695-fda9d18b7981=Agent --agent 7c1e0b2a-5d0e-4c55-9d1e-2f6a8b3c4d5e=Claude"
```

After the `CASES` array, add a refusal table and its loop. Put the loop after the golden loop and before the summary:

```bash
# name | chat-build arguments | expected exit | expected text
REFUSALS=(
  "refuse-old-client-id|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent-client-id x|2|--agent CLIENT_ID=HANDLE"
  "refuse-old-username|rest --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent-username Agent|2|--agent CLIENT_ID=HANDLE"
  "refuse-reserved|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent a=admin|1|reserved handle 'admin'"
  "refuse-duplicate-client|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent a=Agent --agent a=Claude|1|client id 'a' twice"
  "refuse-duplicate-handle|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent a=Claude --agent b=claude|1|handle 'claude' twice"
  "refuse-handle-chars|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent a=my-bot|1|letters, digits and underscores"
  "refuse-shape|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent Agent|1|CLIENT_ID=HANDLE"
  "refuse-no-jwk|rest --run --notls --long --node-id 0 --agent a=Agent|1|--agent requires --jwk PATH"
  "refuse-service|gateway --run --notls --long --node-id 0 --agent a=Agent|1|--agent requires the core, rest or authserv service"
)

for entry in "${REFUSALS[@]}"; do
    IFS='|' read -r name args want_exit want_text <<< "$entry"
    [ -n "$ONLY" ] && [ "$ONLY" != "$name" ] && continue
    [ "$UPDATE" -eq 1 ] && continue
    # shellcheck disable=SC2086
    out="$("$CHAT_BUILD" $args --dry-run 2>&1)"; code=$?
    if [ "$code" -eq "$want_exit" ] && grep -qF -- "$want_text" <<< "$out"; then
        echo "ok    $name"
        pass=$((pass + 1))
    else
        echo "FAIL  $name — exit $code, want $want_exit and \"$want_text\""
        echo "$out" | sed 's/^/        /'
        fail=$((fail + 1))
    fi
done
```

- [ ] **Step 4: Confirm that the cases fail**

```bash
shell-scripts/test-flags.sh > $SCRATCH/t4-red.log 2>&1; echo exit=$?; grep -E "^FAIL" $SCRATCH/t4-red.log
```

Expected: exit 1. The three agent cases and every refusal case fail.

- [ ] **Step 5: Change the arguments**

Replace the three agent arguments in the parser:

```python
    parser.add_argument("--agent", action="append", default=[], metavar="CLIENT_ID=HANDLE",
                        help="an agent: the OAuth client id and the chat user handle. Repeat it for each agent")
    parser.add_argument("--agent-scope", default="chat.mcp",
                        help="scope required by every agent token")
    # Removed by CHAT-frcrctdp. Kept hidden so that a launch that names them
    # fails with a message that names --agent.
    parser.add_argument("--agent-client-id", default=None, help=argparse.SUPPRESS)
    parser.add_argument("--agent-username", default=None, help=argparse.SUPPRESS)
```

Add `import re` beside the other imports. Add these module-level values and the parser function near the top of the build context section:

```python
AGENT_HANDLE = re.compile(r"[A-Za-z0-9_]+")
# Admin and Anon are the ChatIdentity names. Service is the default service
# account. An agent must be a plain user. See CHAT-frcrctdp.
RESERVED_AGENT_HANDLES = ("Admin", "Anon", "Service")


def parse_agents(values: list[str]) -> list[tuple[str, str]]:
    """Parse each --agent value. Exit 1 with a message on a refused value."""
    agents: list[tuple[str, str]] = []
    reserved = {name.lower() for name in RESERVED_AGENT_HANDLES}
    for value in values:
        client_id, sep, handle = value.partition("=")
        if not sep or not client_id or not handle:
            raise SystemExit(f"chat-build: --agent takes CLIENT_ID=HANDLE: {value}")
        if not AGENT_HANDLE.fullmatch(handle):
            raise SystemExit(
                f"chat-build: --agent handle '{handle}' must hold letters, digits and underscores alone")
        if handle.lower() in reserved:
            raise SystemExit(f"chat-build: --agent names reserved handle '{handle}'. "
                             "An agent must be a plain user")
        if any(client_id == c for c, _ in agents):
            raise SystemExit(f"chat-build: --agent names client id '{client_id}' twice")
        if any(handle.lower() == h.lower() for _, h in agents):
            raise SystemExit(f"chat-build: --agent names handle '{handle}' twice")
        agents.append((client_id, handle))
    return agents
```

`SystemExit` with a string prints the string and exits 1.

- [ ] **Step 6: Change the argument checks in `main()`**

Replace the `if args.agent_client_id:` block with:

```python
    if args.agent_client_id or args.agent_username:
        print("chat-build: --agent-client-id and --agent-username are removed. "
              "Use --agent CLIENT_ID=HANDLE. See CHAT-frcrctdp.", file=sys.stderr)
        return 2

    if args.agent:
        if args.service not in ("rest", "core", "authserv"):
            print("chat-build: --agent requires the core, rest or authserv service",
                  file=sys.stderr)
            return 1
        parse_agents(args.agent)
        if args.service in ("rest", "core"):
            if not args.jwk:
                print("chat-build: --agent requires --jwk PATH", file=sys.stderr)
                return 1
            jwk = Path(args.jwk).expanduser()
            if not jwk.is_absolute():
                print(f"chat-build: --jwk must be an absolute path: {args.jwk}",
                      file=sys.stderr)
                return 1
            if not jwk.is_file():
                print(f"chat-build: --jwk names no file: {args.jwk}",
                      file=sys.stderr)
                return 1
```

- [ ] **Step 7: Change the emission**

In `BuildContext.__init__`, replace the two lines that read `agent_client_id` and `agent_username` with:

```python
        self.agents: list[tuple[str, str]] = parse_agents(getattr(args, "agent", []) or [])
```

Replace `agent_flags`:

```python
    def agent_flags(self) -> list[str]:
        """The agent properties of each service. See CHAT-frcrctdp."""
        if not self.agents:
            return []
        if self.service.name == "authserv":
            flags = []
            for i, (client_id, handle) in enumerate(self.agents):
                flags += [f"-Dapp.oauth2.agents[{i}].client-id={client_id}",
                          f"-Dapp.oauth2.agents[{i}].username={handle}"]
            return flags + [f"-Dapp.oauth2.agent-scope={self.agent_scope}"]
        flags = []
        if self.service.name == "core":
            # The bracket form keeps each map key. Without brackets, relaxed
            # binding can give two entries one handle value.
            for _, handle in self.agents:
                flags += [f"-Dapp.init.initial-users[{handle}].handle={handle}",
                          f"-Dapp.init.initial-users[{handle}].name={handle}",
                          f"-Dapp.init.initial-users[{handle}].image-uri=chatimg://agent.png"]
        for i, (client_id, handle) in enumerate(self.agents):
            flags += [f"-Dapp.security.agents[{i}].client-id={client_id}",
                      f"-Dapp.security.agents[{i}].username={handle}"]
        return flags + [f"-Dapp.security.required-scope={self.agent_scope}",
                        f"-Dapp.security.jwt.jwk-path={self.jwk_path}"]
```

- [ ] **Step 8: Write the goldens and read the diff**

```bash
shell-scripts/test-flags.sh --update > $SCRATCH/t4-update.log 2>&1; echo exit=$?; grep -E "^wrote" $SCRATCH/t4-update.log
git diff shell-scripts/golden
```

Expected: `wrote` for the three agent cases alone. Read the diff. The core golden must hold six `initial-users[...]` lines, four `app.security.agents[...]` lines, `required-scope` and `jwk-path`, and no `app.security.agent.` line. The rest golden must hold no `initial-users` line. The authserv golden must hold four `app.oauth2.agents[...]` lines and `app.oauth2.agent-scope=chat.mcp`. Any other change is a defect.

- [ ] **Step 9: Run the flag tests**

```bash
shell-scripts/test-flags.sh > $SCRATCH/t4-green.log 2>&1; echo exit=$?; tail -2 $SCRATCH/t4-green.log
```

Expected: exit 0. The count is 20 golden cases plus 9 refusal cases.

- [ ] **Step 10: Commit**

```bash
git add shell-scripts chat-deploy/src/test chat-webflux/src/test
git commit -F - <<'EOF'
Add the repeatable chat-build --agent flag (CHAT-frcrctdp)

--agent CLIENT_ID=HANDLE emits the agent list on core and rest, the agent
users on core in the bracket map form, and the agent clients on authserv.
The old agent flags exit 2 and name --agent. Binding tests prove the map
merge and the whole list replacement.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 5: Agent clients on the authorization server, memory profile

**Files:**
- Create: `chat-authorization-server/src/main/kotlin/com/demo/chat/config/deploy/authserv/AgentClientProperties.kt`
- Create: `chat-authorization-server/src/main/kotlin/com/demo/chat/config/deploy/authserv/AgentClients.kt`
- Modify: `chat-authorization-server/src/main/kotlin/com/demo/chat/config/deploy/authserv/AuthorizationServerConfig.kt` (annotation and `registeredClientRepo`)
- Modify: `shared-deploy-configuration/src/main/config/oauth2-client.yml`
- Modify: `chat-authorization-server/src/test/resources/application.yml`
- Modify: `chat-authorization-server/src/test/kotlin/com/demo/chat/AccessTokenClaimsTests.kt`
- Create: `chat-authorization-server/src/test/kotlin/com/demo/chat/AgentClientsTests.kt`

**Interfaces:**
- Produces: `AgentClientProperties.agents: List<AgentClient>`, `.agentScope: String?`, `.requireValid(): List<AgentClient>`, `AgentClientProperties.AgentClient(clientId, username)`.
- Produces: `AgentClients.build(clientId: String, scope: String, rawSecret: String): RegisteredClient`, `AgentClients.differences(row: RegisteredClient, scope: String): List<String>`, `AgentClients.requireNoCollision(agentIds: List<String>, sources: Map<String, Collection<String>>)`, `AgentClients.generateSecret(): String`, `AgentClients.TOKEN_TIME_TO_LIVE`.

- [ ] **Step 1: Write the failing unit tests**

```kotlin
package com.demo.chat

import com.demo.chat.config.deploy.authserv.AgentClientProperties
import com.demo.chat.config.deploy.authserv.AgentClients
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import java.time.Duration

class AgentClientsTests {

    private fun properties(scope: String?, vararg clients: Pair<String, String>) = AgentClientProperties().apply {
        agentScope = scope
        agents = clients.map { (id, handle) -> AgentClientProperties.AgentClient().apply { clientId = id; username = handle } }
    }

    @Test
    fun `an agent client has the agent shape`() {
        val client = AgentClients.build("client-a", "chat.mcp", "raw-secret")

        assertThat(client.id).isEqualTo("client-a")
        assertThat(client.clientId).isEqualTo("client-a")
        assertThat(client.authorizationGrantTypes).containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS)
        assertThat(client.clientAuthenticationMethods).containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        assertThat(client.scopes).containsExactly("chat.mcp")
        assertThat(client.clientSettings.isRequireAuthorizationConsent).isFalse()
        assertThat(client.clientSecret).startsWith("{bcrypt}")
        assertThat(client.tokenSettings.accessTokenFormat).isEqualTo(OAuth2TokenFormat.SELF_CONTAINED)
        assertThat(client.tokenSettings.accessTokenTimeToLive).isEqualTo(Duration.ofSeconds(300))
        assertThat(AgentClients.differences(client, "chat.mcp")).isEmpty()
    }

    @Test
    fun `each drifted field is named`() {
        val good = AgentClients.build("client-a", "chat.mcp", "raw-secret")
        fun drift(change: RegisteredClient.Builder.() -> Unit) =
            AgentClients.differences(RegisteredClient.from(good).apply(change).build(), "chat.mcp")

        assertThat(drift { authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE); redirectUri("http://x") })
            .containsExactly("grant")
        assertThat(drift { clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST) })
            .containsExactly("authentication method")
        assertThat(drift { scope("openid") }).containsExactly("scope")
        assertThat(drift { clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build()) })
            .containsExactly("consent")
        assertThat(drift { clientSecret("{noop}raw-secret") }).containsExactly("secret prefix")
        assertThat(drift {
            tokenSettings(TokenSettings.withSettings(good.tokenSettings.settings)
                .accessTokenFormat(OAuth2TokenFormat.REFERENCE).build())
        }).containsExactly("token format")
        assertThat(drift {
            tokenSettings(TokenSettings.withSettings(good.tokenSettings.settings)
                .accessTokenTimeToLive(Duration.ofMinutes(30)).build())
        }).containsExactly("token lifetime")
    }

    @Test
    fun `a generated secret is 32 bytes without padding`() {
        val secret = AgentClients.generateSecret()

        assertThat(java.util.Base64.getUrlDecoder().decode(secret)).hasSize(32)
        assertThat(secret).doesNotContain("=")
    }

    @Test
    fun `agents without a scope fail and name the property`() {
        assertThatThrownBy { properties(null, "client-a" to "Agent").requireValid() }
            .hasMessage("app.oauth2.agent-scope is required when app.oauth2.agents is set.")
    }

    @Test
    fun `no agents need no scope`() {
        assertThat(properties(null).requireValid()).isEmpty()
    }

    @Test
    fun `a shared agent client id fails and names it`() {
        assertThatThrownBy { properties("chat.mcp", "client-a" to "Agent", "client-a" to "Claude").requireValid() }
            .hasMessage("app.oauth2.agents names client id 'client-a' twice.")
    }

    @Test
    fun `an agent client id in another source fails and names both`() {
        assertThatThrownBy {
            AgentClients.requireNoCollision(listOf("client-a"), mapOf("app.oauth2.client" to listOf("client-a")))
        }.hasMessage("Agent client id 'client-a' is also registered by app.oauth2.client.")
    }
}
```

- [ ] **Step 2: Confirm that the tests fail**

```bash
LOG=$SCRATCH/t5-red.log; mvn -o -B -pl chat-authorization-server -am test-compile > $LOG 2>&1; echo exit=$?; grep -E "ERROR.*\.kt" $LOG | head -3
```

Expected: exit 1. `AgentClients` does not exist.

- [ ] **Step 3: Write `AgentClientProperties.kt`**

```kotlin
package com.demo.chat.config.deploy.authserv

import com.demo.chat.domain.ChatException
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The agent clients that this authorization server issues tokens to. See
 * `CHAT-frcrctdp`. `chat-build authserv --agent` sets them.
 */
@ConfigurationProperties("app.oauth2")
class AgentClientProperties {

    var agents: List<AgentClient> = emptyList()

    /** The one scope of every agent client. This deployment selects `chat.mcp`. */
    var agentScope: String? = null

    class AgentClient {
        var clientId: String = ""
        var username: String = ""
    }

    fun requireValid(): List<AgentClient> {
        if (agents.isEmpty()) return agents
        if (agentScope.isNullOrBlank()) {
            throw ChatException("app.oauth2.agent-scope is required when app.oauth2.agents is set.")
        }
        agents.forEachIndexed { index, agent ->
            if (agent.clientId.isBlank()) throw ChatException("app.oauth2.agents[$index].client-id is required.")
            if (agent.username.isBlank()) throw ChatException("app.oauth2.agents[$index].username is required.")
        }
        agents.groupBy { it.clientId }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
            throw ChatException("app.oauth2.agents names client id '$it' twice.")
        }
        return agents
    }
}
```

- [ ] **Step 4: Write `AgentClients.kt`**

```kotlin
package com.demo.chat.config.deploy.authserv

import com.demo.chat.domain.ChatException
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

/**
 * One `client_credentials` client per agent. See `CHAT-frcrctdp`.
 *
 * **The token settings are explicit.** REST and the core decode a JWT
 * locally, so an opaque token would answer 401 on every call. The factory does
 * not rely on the library defaults.
 */
object AgentClients {

    val TOKEN_TIME_TO_LIVE: Duration = Duration.ofSeconds(300)
    private const val SECRET_BYTES = 32
    private const val BCRYPT_PREFIX = "{bcrypt}"
    private val random = SecureRandom()
    private val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    fun generateSecret(): String {
        val bytes = ByteArray(SECRET_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun build(clientId: String, scope: String, rawSecret: String): RegisteredClient =
        RegisteredClient.withId(clientId)
            .clientId(clientId)
            .clientSecret(encoder.encode(rawSecret))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope(scope)
            .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
            .tokenSettings(
                TokenSettings.builder()
                    .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                    .accessTokenTimeToLive(TOKEN_TIME_TO_LIVE)
                    .build()
            )
            .build()

    /** The agent shape fields in which [row] differs. An empty answer means the shape holds. */
    fun differences(row: RegisteredClient, scope: String): List<String> = buildList {
        if (row.authorizationGrantTypes != setOf(AuthorizationGrantType.CLIENT_CREDENTIALS)) add("grant")
        if (row.clientAuthenticationMethods != setOf(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)) {
            add("authentication method")
        }
        if (row.scopes != setOf(scope)) add("scope")
        if (row.clientSettings.isRequireAuthorizationConsent) add("consent")
        if (row.clientSecret?.startsWith(BCRYPT_PREFIX) != true) add("secret prefix")
        if (row.tokenSettings.accessTokenFormat != OAuth2TokenFormat.SELF_CONTAINED) add("token format")
        if (row.tokenSettings.accessTokenTimeToLive != TOKEN_TIME_TO_LIVE) add("token lifetime")
    }

    fun requireNoCollision(agentIds: List<String>, sources: Map<String, Collection<String>>) {
        agentIds.forEach { id ->
            sources.entries.firstOrNull { id in it.value }?.let {
                throw ChatException("Agent client id '$id' is also registered by ${it.key}.")
            }
        }
    }

    /** Build a new client per agent, and print each secret once. */
    fun newClients(agents: List<AgentClientProperties.AgentClient>, scope: String): List<RegisteredClient> =
        agents.map { agent ->
            val secret = generateSecret()
            println("Generated secret for agent client '${agent.clientId}' (${agent.username}): $secret")
            build(agent.clientId, scope, secret)
        }

    /**
     * Save an absent agent client, keep a matching one, and refuse any other
     * row. A refused row fails the start. The operator deletes it, and the
     * next start registers it again.
     */
    fun reconcile(repository: RegisteredClientRepository, agents: List<AgentClientProperties.AgentClient>, scope: String) {
        agents.forEach { agent ->
            val row = repository.findByClientId(agent.clientId)
            if (row == null) {
                repository.save(newClients(listOf(agent), scope).single())
                return@forEach
            }
            val drift = differences(row, scope)
            if (drift.isNotEmpty()) {
                throw ChatException(
                    "The stored client '${agent.clientId}' is not an agent client. It differs in " +
                        "${drift.joinToString()}. Delete the row, and the next start registers it again."
                )
            }
            println("Agent client '${agent.clientId}' (${agent.username}) is registered. Its secret is unchanged.")
        }
    }
}
```

- [ ] **Step 5: Run the unit tests**

```bash
LOG=$SCRATCH/t5-unit.log; mvn -o -B -pl chat-authorization-server -am test -Dtest=AgentClientsTests \
  -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|<<< FAIL" $LOG | tail -3
```

Expected: exit 0, 7 tests.

- [ ] **Step 6: Register the agent clients in the memory repository**

In `AuthorizationServerConfig`, change the annotation to `@EnableConfigurationProperties(Oauth2ClientProperties::class, AgentClientProperties::class)`. Replace `registeredClientRepo`:

```kotlin
    /**
     * The memory repository holds the configured client and every agent
     * client. Each start makes a new secret for every agent. See
     * `CHAT-frcrctdp`.
     */
    @Profile("memory")
    @Bean
    fun registeredClientRepo(
        clientProps: Oauth2ClientProperties,
        agentProps: AgentClientProperties,
        serverProps: ObjectProvider<OAuth2AuthorizationServerProperties>,
    ): RegisteredClientRepository {
        val agents = agentProps.requireValid()
        AgentClients.requireNoCollision(
            agents.map { it.clientId },
            mapOf(
                "app.oauth2.client" to listOf(clientProps.clientId),
                "spring.security.oauth2.authorizationserver.client" to bootClientIds(serverProps),
            ),
        )
        val agentClients = agentProps.agentScope?.let { AgentClients.newClients(agents, it) }.orEmpty()
        return InMemoryRegisteredClientRepository(listOf(RegisteredClientFactory(clientProps)()) + agentClients)
    }
```

Add this top-level function to `AgentClients.kt`, because Task 6 also uses it:

```kotlin
/** The client ids of the `spring.security.oauth2.authorizationserver.client` map. */
fun bootClientIds(serverProps: ObjectProvider<OAuth2AuthorizationServerProperties>): List<String> =
    serverProps.ifAvailable?.client?.values?.mapNotNull { it.registration.clientId }.orEmpty()
```

Imports for `AgentClients.kt`: `org.springframework.beans.factory.ObjectProvider` and `org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties`. Imports for `AuthorizationServerConfig.kt`: `org.springframework.beans.factory.ObjectProvider` and the same properties class.

- [ ] **Step 7: Remove `chat.mcp` from `chat-client`**

In `shared-deploy-configuration/src/main/config/oauth2-client.yml` and in `chat-authorization-server/src/test/resources/application.yml`, delete the line `        - chat.mcp` under `additional-scopes`.

- [ ] **Step 8: Move the token tests to agent clients**

In `AccessTokenClaimsTests`:

- Add `@ExtendWith(OutputCaptureExtension::class)` to the class. Import `org.junit.jupiter.api.extension.ExtendWith`, `org.springframework.boot.test.system.OutputCaptureExtension` and `org.springframework.boot.test.system.CapturedOutput`.
- Add these properties to the `@SpringBootTest` list:

```kotlin
        "app.oauth2.agent-scope=chat.mcp",
        "app.oauth2.agents[0].client-id=client-agent",
        "app.oauth2.agents[0].username=Agent",
        "app.oauth2.agents[1].client-id=client-claude",
        "app.oauth2.agents[1].username=Claude",
```

- Replace `accessToken()` and the refresh test request with a helper that takes the client and its secret:

```kotlin
    private fun secretOf(output: CapturedOutput, clientId: String): String =
        Regex("Generated secret for agent client '$clientId' \\(\\w+\\): (\\S+)")
            .findAll(output.out).last().groupValues[1]

    private fun tokenResponse(clientId: String, secret: String, scope: String) =
        WebClient.create("http://localhost:$port")
            .post()
            .uri("/oauth2/token")
            .headers { it.setBasicAuth(clientId, secret) }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData("grant_type", "client_credentials").with("scope", scope))
            .exchangeToMono { response -> response.bodyToMono(Map::class.java).map { response.statusCode().value() to it } }
            .block()!!

    private fun accessToken(output: CapturedOutput, clientId: String): String {
        val (status, body) = tokenResponse(clientId, secretOf(output, clientId), "chat.mcp")
        assertThat(status).isEqualTo(200)
        return body["access_token"] as String
    }
```

- Each existing test takes `output: CapturedOutput` as a parameter and uses `accessToken(output, "client-agent")`. The `client_id` assertion expects `"client-agent"`.
- Add:

```kotlin
    @Test
    fun `each agent client receives a self contained token for its own client id`(output: CapturedOutput) {
        listOf("client-agent", "client-claude").forEach { clientId ->
            val claims = SignedJWT.parse(accessToken(output, clientId)).jwtClaimsSet

            assertThat(claims.getStringClaim("client_id")).isEqualTo(clientId)
            assertThat(claims.getStringListClaim("scope")).containsExactly("chat.mcp")
            assertThat(java.time.Duration.between(claims.issueTime.toInstant(), claims.expirationTime.toInstant()))
                .isEqualTo(java.time.Duration.ofSeconds(300))
        }
    }

    @Test
    fun `the configured client no longer receives the agent scope`() {
        val (status, body) = tokenResponse(clientProperties.clientId, "secret", "chat.mcp")

        assertThat(status).isEqualTo(400)
        assertThat(body["error"]).isEqualTo("invalid_scope")
    }
```

`SignedJWT.parse(...)` proves that the token is a JWT and not an opaque reference token. A `{bcrypt}` secret that the server did not accept would answer 401, so the 200 status proves the encoding.

- [ ] **Step 9: Run the authorization server tests**

```bash
LOG=$SCRATCH/t5-green.log; mvn -o -B -pl chat-authorization-server -am test -Dtest='AgentClientsTests,AccessTokenClaimsTests,AuthorizationCodeFlowTests,AuthorizationServerDeployTests' \
  -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|<<< FAIL|Caused by" $LOG | tail -6
```

Expected: exit 0. If `AuthorizationCodeFlowTests` requested `chat.mcp` from `chat-client`, it now fails with `invalid_scope`. Change that request to a scope that `chat-client` still holds, such as `openid`, and record the change in the commit message.

- [ ] **Step 10: Commit**

```bash
git add -A chat-authorization-server shared-deploy-configuration
git commit -F - <<'EOF'
Issue one client per agent on the authorization server (CHAT-frcrctdp)

The memory repository holds one client_credentials client per agent, with
a generated secret that the start prints once. The client issues
self-contained tokens with a 300 second lifetime. chat-client loses the
chat.mcp scope. A shared client id fails the start.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 6: Agent clients on the authorization server, client-init profile

**Files:**
- Modify: `chat-authorization-server/src/main/kotlin/com/demo/chat/config/deploy/authserv/ClientLoader.kt`
- Modify: `chat-authorization-server/src/test/kotlin/com/demo/chat/ClientInitializerTest.kt`

**Interfaces:**
- Consumes: `AgentClients.reconcile`, `AgentClients.requireNoCollision`, `bootClientIds`, `AgentClientProperties.requireValid()` from Task 5.
- Produces: `ClientInitializer(repo, mapper, agentProps)` with a new runner bean `registerAgentClients(serverProps, clientProps)`.

- [ ] **Step 1: Write the failing tests**

Add to `ClientInitializerTest`:

```kotlin
    private fun agentProps() = AgentClientProperties().apply {
        agentScope = "chat.mcp"
        agents = listOf(AgentClientProperties.AgentClient().apply { clientId = "client-agent"; username = "Agent" })
    }

    private fun runner(agentProps: AgentClientProperties, vararg args: String) =
        ClientInitializer(repo, mapper, agentProps)
            .registerAgentClients(serverProps(), Oauth2ClientProperties().apply { clientId = "chat-client-id" })
            .run(DefaultApplicationArguments(*args))

    private fun serverProps(): ObjectProvider<OAuth2AuthorizationServerProperties> {
        val props = OAuth2AuthorizationServerProperties().apply {
            client["chat-client"] = OAuth2AuthorizationServerProperties.Client().apply {
                registration.clientId = "chatClient"
            }
        }
        return StaticListableBeanFactory(mapOf("props" to props)).getBeanProvider(OAuth2AuthorizationServerProperties::class.java)
    }

    @Test
    fun `an absent agent client is saved once`() {
        Mockito.`when`(repo.findByClientId("client-agent")).thenReturn(null)

        runner(agentProps())

        val saved = ArgumentCaptor.forClass(RegisteredClient::class.java)
        Mockito.verify(repo, Mockito.times(1)).save(saved.capture())
        assertThat(AgentClients.differences(saved.value, "chat.mcp")).isEmpty()
    }

    @Test
    fun `a matching agent client is kept`() {
        Mockito.`when`(repo.findByClientId("client-agent"))
            .thenReturn(AgentClients.build("client-agent", "chat.mcp", "kept"))

        runner(agentProps())

        Mockito.verify(repo, Mockito.never()).save(Mockito.any())
    }

    @ParameterizedTest
    @ValueSource(strings = ["grant", "authentication method", "scope", "consent", "secret prefix", "token format", "token lifetime"])
    fun `a drifted agent row fails the start and names the field`(field: String) {
        val good = AgentClients.build("client-agent", "chat.mcp", "kept")
        val drifted = RegisteredClient.from(good).apply {
            when (field) {
                "grant" -> authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://x")
                "authentication method" -> clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                "scope" -> scope("openid")
                "consent" -> clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                "secret prefix" -> clientSecret("{noop}kept")
                "token format" -> tokenSettings(TokenSettings.withSettings(good.tokenSettings.settings)
                    .accessTokenFormat(OAuth2TokenFormat.REFERENCE).build())
                "token lifetime" -> tokenSettings(TokenSettings.withSettings(good.tokenSettings.settings)
                    .accessTokenTimeToLive(Duration.ofMinutes(30)).build())
            }
        }.build()
        Mockito.`when`(repo.findByClientId("client-agent")).thenReturn(drifted)

        assertThatThrownBy { runner(agentProps()) }
            .hasMessageContaining("The stored client 'client-agent' is not an agent client")
            .hasMessageContaining(field)
        Mockito.verify(repo, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `an agent id that a boot client uses fails the start`() {
        val props = agentProps().apply { agents.single().clientId = "chatClient" }

        assertThatThrownBy { runner(props) }
            .hasMessage("Agent client id 'chatClient' is also registered by spring.security.oauth2.authorizationserver.client.")
    }

    @Test
    fun `an agent id that the clientpath file uses fails the start`() {
        val props = agentProps().apply { agents.single().clientId = "ba89bb6f-8cf9-4b39-8118-2bf917b19bee" }

        assertThatThrownBy { runner(props, "--clientpath=classpath:testclient.json") }
            .hasMessage("Agent client id 'ba89bb6f-8cf9-4b39-8118-2bf917b19bee' is also registered by --clientpath.")
    }
```

Change the existing `test runner` test to construct `ClientInitializer(repo, mapper, AgentClientProperties())`. Add the imports that these tests name: `AgentClientProperties`, `AgentClients`, `ObjectProvider`, `StaticListableBeanFactory`, `OAuth2AuthorizationServerProperties`, `AuthorizationGrantType`, `ClientAuthenticationMethod`, `ClientSettings`, `TokenSettings`, `OAuth2TokenFormat`, `Duration`, `ParameterizedTest`, `ValueSource`, and AssertJ `assertThat` and `assertThatThrownBy`.

- [ ] **Step 2: Confirm that the tests fail**

```bash
LOG=$SCRATCH/t6-red.log; mvn -o -B -pl chat-authorization-server -am test-compile > $LOG 2>&1; echo exit=$?; grep -E "ERROR.*\.kt" $LOG | head -3
```

Expected: exit 1. `registerAgentClients` does not exist.

- [ ] **Step 3: Change `ClientInitializer`**

- Add the constructor parameter `val agentProps: AgentClientProperties`, and annotate the class with `@EnableConfigurationProperties(AgentClientProperties::class)`.
- Change `loadOauth2AuthorizationServerProperties` so that it registers every entry, not only `chat-client`. Wrap the body in `properties.client.forEach { (name, client) -> ... }`, and name the entry in each error: `"The $name registration carries no client id"`.
- Add the agent runner. It runs first, so that it checks the other sources before they save:

```kotlin
    /**
     * Registers one client per agent. See `CHAT-frcrctdp`.
     *
     * It runs before the other runners, so a collision fails the start before
     * any other client is saved.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun registerAgentClients(
        serverProps: ObjectProvider<OAuth2AuthorizationServerProperties>,
        clientProps: Oauth2ClientProperties,
    ): ApplicationRunner = ApplicationRunner { args ->
        val agents = agentProps.requireValid()
        if (agents.isEmpty()) return@ApplicationRunner
        AgentClients.requireNoCollision(
            agents.map { it.clientId },
            mapOf(
                "app.oauth2.client" to listOf(clientProps.clientId),
                "spring.security.oauth2.authorizationserver.client" to bootClientIds(serverProps),
                "--clientpath" to clientPathIds(args),
            ),
        )
        AgentClients.reconcile(repo, agents, agentProps.agentScope!!)
    }

    private fun clientPathIds(args: ApplicationArguments): List<String> =
        args.getOptionValues("clientpath")?.firstOrNull()?.let { listOf(readClientPath(it).clientId) }.orEmpty()

    private fun readClientPath(clientPath: String): Oauth2ClientProperties {
        val resource = if (clientPath.startsWith("classpath:")) {
            ClassPathResource(clientPath.substring(10))
        } else {
            UrlResource(File(clientPath).toURI().toURL())
        }
        return mapper.readValue(resource.inputStream, Oauth2ClientProperties::class.java)
    }
```

- In `loadClient`, replace the inline resource read with `readClientPath(clientPath)`.
- Imports: `org.springframework.boot.ApplicationArguments`, `org.springframework.beans.factory.ObjectProvider`, `org.springframework.boot.context.properties.EnableConfigurationProperties`, `org.springframework.core.Ordered`, `org.springframework.core.annotation.Order`.

- [ ] **Step 4: Run the tests**

```bash
LOG=$SCRATCH/t6-green.log; mvn -o -B -pl chat-authorization-server -am test -Dtest='ClientInitializerTest,AgentClientsTests' \
  -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|<<< FAIL" $LOG | tail -3
```

Expected: exit 0.

- [ ] **Step 5: Prove the shape check with a mutation**

In `AgentClients.differences`, delete the `token format` line. Run the Step 4 command. Expected: the `token format` case fails. Restore the file by absolute path, and run `git status --short chat-authorization-server`.

- [ ] **Step 6: File the `client.json` defect**

```bash
fp issue create --title "chat-build authserv passes --clientpath to a missing client.json" --parent CHAT-aqpcacwv --priority low \
  --description "chat-build authserv passes --clientpath='classpath:client.json'. Main resources hold no client.json. Under the client-init profile, ClientInitializer.loadClient reads that path and the start fails. Found under CHAT-frcrctdp. Not repaired there."
```

- [ ] **Step 7: Commit**

```bash
git add -A chat-authorization-server
git commit -F - <<'EOF'
Reconcile agent clients on the client-init path (CHAT-frcrctdp)

ClientInitializer registers every Boot client entry, not chat-client alone.
An absent agent client is saved and its secret printed once. A matching row
is kept. A row that differs in any agent shape field fails the start and
names the field. An agent id that another source holds fails the start.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 7: The two-process relay test with two agents

**Files:**
- Modify: `chat-deploy-memory-integration-test/src/test/kotlin/com/demo/chat/deploy/test/security/RestToCoreBearerDeploymentTests.kt`

**Interfaces:**
- Consumes: `DeployTestSigningKey.mint(clientId: String, scope: String): String`.

- [ ] **Step 1: Change the launch flags**

In `coreFlags`, replace the three `-Dapp.security.agent.*` lines with:

```kotlin
        "-Dapp.init.initial-users[Claude].handle=Claude",
        "-Dapp.init.initial-users[Claude].name=Claude",
        "-Dapp.init.initial-users[Claude].image-uri=chatimg://agent.png",
        "-Dapp.security.required-scope=chat.mcp",
        "-Dapp.security.agents[0].client-id=client-under-test",
        "-Dapp.security.agents[0].username=Agent",
        "-Dapp.security.agents[1].client-id=client-claude",
        "-Dapp.security.agents[1].username=Claude",
```

In `restFlags`, replace the three lines with the last five lines above. REST creates no user.

In `startDeployments`, replace each `"--agent-client-id", "client-under-test", "--agent-username", "Agent",` with `"--agent", "client-under-test=Agent", "--agent", "client-claude=Claude",`. Those arguments document the launch. The helper reads `--jwk` alone.

- [ ] **Step 2: Generalize the owner check**

Replace `assertOwnerIsConfiguredAgent(roomId: String)` with `assertOwnerIs(roomId: String, handle: String)`. The body reads `ByStringRequest(handle)` in place of `ByStringRequest("Agent")`. Change the one caller to `assertOwnerIs(id!!, "Agent")`.

- [ ] **Step 3: Add the two-agent test and the unlisted test**

```kotlin
    @Test
    fun `each REST agent token reaches core authorization as its own identity`() {
        listOf("client-under-test" to "Agent", "client-claude" to "Claude").forEach { (clientId, handle) ->
            val token = DeployTestSigningKey.mint(clientId, "chat.mcp")
            val created = request(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$restPort/topic/new"))
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"type\":\"ByNameRequest\",\"name\":\"relay${handle.lowercase()}\"}"))
                    .build(),
            )

            assertThat(created.statusCode()).describedAs("the room add of $handle").isEqualTo(201)
            val id = Regex("\\\"id\\\"\\s*:\\s*(\\d+)").find(created.body())!!.groupValues[1]
            assertOwnerIs(id, handle)
        }
    }

    @Test
    fun `a token from an unlisted client answers 401 on REST`() {
        val response = request(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$restPort/topic/list"))
                .header("Authorization", "Bearer ${DeployTestSigningKey.mint("client-unlisted", "chat.mcp")}")
                .GET().build(),
        )

        assertThat(response.statusCode()).isEqualTo(401)
    }
```

Room names hold no hyphen, because of the Lucene name token defect `CHAT-hajmhslp`.

- [ ] **Step 4: Run the opt-in test**

```bash
LOG=$SCRATCH/t7.log; mvn -B -pl chat-deploy-memory-integration-test -am verify \
  -Prest-core-e2e -Dtest=RestToCoreBearerDeploymentTests \
  -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|<<< FAIL|Caused by" $LOG | tail -5
```

Expected: exit 0. Every test of the class passes, the two new tests included.

- [ ] **Step 5: Mutate the core wiring through the relay**

Apply the Task 3 Step 6 mutation. Run the Step 4 command. Expected: `each REST agent token reaches core authorization as its own identity` fails for `Claude`. Restore by absolute path and run `git status --short`.

- [ ] **Step 6: Commit**

```bash
git add chat-deploy-memory-integration-test
git commit -F - <<'EOF'
Relay two agents from REST to the core (CHAT-frcrctdp)

Both processes take two agents. Each agent adds a room through REST, and
each owner row names its own agent key. A token from an unlisted client
answers 401 on REST.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 8: Documents, the vector gate, drift, and the register

**Files:**
- Modify: `docs/MCP-CREDENTIAL-ISSUANCE.md`, `docs/REST-TOKEN-RELAY.md`, `docs/BUILD.md`, `docs/EMBEDDING-PROVIDERS.md`, `docs/VECTOR-RECALL-API.md`, `docs/MCP-ADAPTER.md`, `docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md`, `shell-scripts/README-chat-build.md`
- Modify: `shell-scripts/vector/gate-embedding-launch.sh:155-170`
- Modify: `drift.lock`
- Modify: `forward-register.md`

- [ ] **Step 1: Update the vector gate**

In `gate-embedding-launch.sh`, replace the three `--app.security.agent.*` arguments with:

```bash
    --app.security.required-scope="$AGENT_SCOPE" \
    --app.security.agents[0].client-id="$AGENT_CLIENT_ID" \
    --app.security.agents[0].username="$AGENT_USERNAME" \
```

Quote each argument as shown. `zsh` and `bash` both read `[0]` as a glob when it is not quoted.

- [ ] **Step 2: Rewrite the credential procedure**

In `docs/MCP-CREDENTIAL-ISSUANCE.md`:

- The `Claim client_id` row reads: `The client id of one app.security.agents entry in the deployment`.
- The paragraph after the table reads: **The `client_id` claim selects the agent.** One client id appears three times. It names the OAuth client in the token request. It names one `app.security.agents` entry at the deployment, and that entry names the chat user. A token from an unlisted client is refused with 401, even when its scope is right.
- Step 1 adds one `--agent` per agent to the `chat-build authserv` command:

```sh
./shell-scripts/chat-build authserv --run --notls --node-id 8 \
  --jwk "$PWD/encrypt-keys/server_keycert.jwk" --profile memory \
  --agent 31649af5-0154-4be5-8695-fda9d18b7981=Agent \
  --agent 7c1e0b2a-5d0e-4c55-9d1e-2f6a8b3c4d5e=Claude
```

  Add: The start prints one line per agent: `Generated secret for agent client '<id>' (<handle>): <secret>`. Record each secret. **The memory profile makes new secrets at each start.** Request a new token after a restart. The `client-init` profile saves a client once and prints its secret once.

- Steps 2 and 3 name `'<agent-client-id>:<printed-secret>'` in the `curl -u` value.
- Step 4 replaces the property table with:

| Property | Value |
|---|---|
| `app.security.agents[n].client-id` | The client id of agent `n` |
| `app.security.agents[n].username` | The chat user that names agent `n` |
| `app.security.required-scope` | `chat.mcp` |
| `app.security.jwt.jwk-path` | The JWK file that holds the trusted public key |

- Step 4 commands: the core takes `--agent` for every agent, and it creates each agent user. REST takes the same `--agent` list:

```sh
./shell-scripts/chat-build core --memory --run --notls --node-id 1 --init users,rootkeys \
  --jwk "$PWD/encrypt-keys/server_keycert.jwk" \
  --agent 31649af5-0154-4be5-8695-fda9d18b7981=Agent \
  --agent 7c1e0b2a-5d0e-4c55-9d1e-2f6a8b3c4d5e=Claude
./shell-scripts/chat-build rest --run --notls --node-id 2 \
  --jwk "$PWD/encrypt-keys/server_keycert.jwk" \
  --agent 31649af5-0154-4be5-8695-fda9d18b7981=Agent \
  --agent 7c1e0b2a-5d0e-4c55-9d1e-2f6a8b3c4d5e=Claude
```

  Add: **The core and REST must carry the same list.** No check compares them. A client that REST lists and the core does not answers 401.
- The resolution sentence reads: `The agent username '<name>' for client '<id>' answered <n> users. It must answer exactly one user.` Add: A handle must not be `Admin`, `Anon` or a service account, in any case.
- Delete `Limit 3`. Add one sentence where it stood: The `client-init` profile registers one `chat.mcp` client per `--agent` since `CHAT-frcrctdp`.
- In the appendix flag list, replace the three `app.security.agent.*` flags with the four new properties.

- [ ] **Step 3: Update the other documents**

- `docs/BUILD.md:174-186`: each `--agent-client-id <client-id> --agent-username <handle>` becomes `--agent <client-id>=<handle>`, and the sentence lists the emitted properties of the Task 4 table. Line 216 reads: Use `Agent` as the handle of the first agent. `userinit.yml` declares that handle. Add: `--agent` repeats once per agent. A handle holds letters, digits and underscores alone.
- `docs/EMBEDDING-PROVIDERS.md:182-184`: the three flags become `--app.security.required-scope=chat.mcp`, `'--app.security.agents[0].client-id=<client-id>'` and `'--app.security.agents[0].username=Agent'`. Line 212 names `app.security.required-scope is required.`
- `docs/VECTOR-RECALL-API.md:265`: `app.security.agent.username` becomes `the app.security.agents entry of the token client`.
- `docs/MCP-ADAPTER.md`: find each `app.security.agent` and `--agent-client-id` with `grep -n`, and apply the same mapping.
- `docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md`: add one paragraph at the top: These runs used the single agent keys of their date. Since `CHAT-frcrctdp`, a launch names agents with `app.security.agents[n]`. See `docs/MCP-CREDENTIAL-ISSUANCE.md`.
- `shell-scripts/README-chat-build.md`: document `--agent CLIENT_ID=HANDLE`, the three service tables of Task 4, and the refusals.

- [ ] **Step 4: Review `REST-TOKEN-RELAY.md`, then relink**

```bash
drift check; drift refs docs/REST-TOKEN-RELAY.md
```

Expected: STALE, because Tasks 1 and 2 changed bound files. Read each section of `docs/REST-TOKEN-RELAY.md` against the code. Change line 78 to `RC->>RC: client_id selects an app.security.agents entry`. Add a section `## More than one agent (CHAT-frcrctdp)` that states the selection rule, the 401 for an unlisted client, and the two-lists rule of the spec. Then:

```bash
drift unlink docs/REST-TOKEN-RELAY.md chat-security/src/main/kotlin/com/demo/chat/config/agent/AgentIdentity.kt
drift link docs/REST-TOKEN-RELAY.md chat-security/src/main/kotlin/com/demo/chat/config/agent/AgentIdentities.kt
drift link docs/REST-TOKEN-RELAY.md chat-security/src/main/kotlin/com/demo/chat/config/agent/AgentSecurityPropertiesGuard.kt
drift link docs/REST-TOKEN-RELAY.md
drift check
```

If `drift unlink` does not exist in drift v0.7.0, run `drift --help`, and remove the anchor with the command that it lists. Expected for the last command: `ok`.

- [ ] **Step 5: Check for stale references**

```bash
grep -rnE "agent-client-id|agent-username|app\.security\.agent\." docs/*.md shell-scripts/*.md shell-scripts/vector shell-scripts/chat-build | grep -v "removed\|CHAT-frcrctdp\|is replaced"
```

Expected: no line outside `docs/superpowers/` and the register.

- [ ] **Step 6: Add the register section**

Append to `forward-register.md`:

```markdown
## More than one agent on one REST deployment (2026-10-05)

`CHAT-frcrctdp`. Branch `chat-frcrctdp-multi-agent`. **This work is not
merged.** Spec: `docs/superpowers/specs/2026-10-05-multi-agent-rest-design.md`.
Plan: `docs/superpowers/plans/2026-10-05-multi-agent-rest.md`.

### What changed

- `app.security.agents[n]` and `app.security.required-scope` replace
  `app.security.agent.*`. The old keys fail the start.
- The token `client_id` selects the agent identity, on REST and on the core.
  An unlisted client answers 401 and `0x401`.
- `chat-build --agent CLIENT_ID=HANDLE` repeats once per agent. On `core` it
  also creates the agent user, in the bracket form
  `app.init.initial-users[<H>]`.
- The authorization server issues one `client_credentials` client per agent.
  It prints each generated secret once. `chat-client` lost `chat.mcp`.

### Rules that are easy to lose

- **Handle checks ignore case.** The Lucene user index lowercases the handle,
  so a lookup for `admin` answers `Admin`. The lifecycle keeps an exact match.
- **The core and REST must carry the same list.** No check compares them.
- **The memory profile makes new agent secrets at each start.**
- **A map key without brackets can lose distinct values.** `Bot_1` and `Bot1`
  received one handle value in a Binder probe.

### Measured

Fill each line from the run logs of Task 9. Do not write a number that no run
produced.
```

- [ ] **Step 7: Commit**

```bash
git add -A docs shell-scripts forward-register.md drift.lock
git commit -F - <<'EOF'
Document more than one agent per deployment (CHAT-frcrctdp)

The credential procedure issues one client per agent and launches the core
and REST with the same --agent list. REST-TOKEN-RELAY.md states the client
id selection, and its drift binding names AgentIdentities.kt.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 9: Gates, evidence, and the pull request

**Files:**
- Modify: `forward-register.md` (the `Measured` list of Task 8)
- Modify: `docs/BUILD-HEALTH.md` (one run line)

- [ ] **Step 1: Run the default gate**

```bash
LOG=$SCRATCH/t9-default.log; shell-scripts/build-health.sh > $LOG 2>&1; echo exit=$?; tail -6 $LOG
```

Expected: exit 0, and no drift.

- [ ] **Step 2: Run the CI gate**

Check `docker events --since 1h --filter event=oom` first. Another session must not run container tests at the same time.

```bash
docker image inspect --format '{{.Id}}' docker.io/library/chat-deploy-long-memory-integration-test:0.0.1 > $SCRATCH/image-before.txt
LOG=$SCRATCH/t9-ci.log; DOCKER_CONFIG=$(mktemp -d) shell-scripts/build-health.sh --ci > $LOG 2>&1; echo exit=$?; tail -8 $LOG
docker image inspect --format '{{.Id}}' docker.io/library/chat-deploy-long-memory-integration-test:0.0.1
cat $SCRATCH/image-before.txt
```

Expected: exit 0, no drift, and an image id that differs from the one before. A test count that moved must match the tests that this plan added.

- [ ] **Step 3: Record the readings**

Fill the `Measured` list of the register section with the real numbers of Steps 1 and 2, the Task 7 run, and each mutation of Tasks 2, 3, 6 and 7. Add one line to `docs/BUILD-HEALTH.md` in the style of the earlier run lines: branch, test count, skipped count, image id.

```bash
git add forward-register.md docs/BUILD-HEALTH.md
git commit -m "Record the multi-agent gate readings (CHAT-frcrctdp)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 4: Comment on the issue**

```bash
fp comment CHAT-frcrctdp "Implementation complete on chat-frcrctdp-multi-agent. <one line per acceptance item, with the run that measured it>. Not measured: secret rotation, and a check that the REST and core lists agree."
```

- [ ] **Step 5: Push and open the pull request**

Ask the owner before you push. Then:

```bash
git push -u origin chat-frcrctdp-multi-agent
gh pr create --title "Serve more than one agent on one REST deployment (CHAT-frcrctdp)" --body-file $SCRATCH/pr-body.md
```

Write `$SCRATCH/pr-body.md` from the register section. End it with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`. Do not merge. The owner merges.

---

## Spec Coverage

| Spec requirement | Task |
|---|---|
| Agent list and scope binding, duplicates, empty list | 1 |
| Reserved handles, case rule, service accounts | 2 |
| Old keys fail the start | 2, and Task 3 for REST |
| `AgentIdentities`, converter lookup, unknown client 401 | 1, 3 |
| Exact handle filter, one key per client | 1 |
| REST selection measured apart from the core | 3 |
| `chat-build --agent`, handle characters, refusals, emission | 4 |
| Bracket form, map merge, list replacement proofs | 4 |
| Agent client factory, token settings, `{bcrypt}` | 5 |
| Memory repository, secret printed, collision with `app.oauth2.client` and the Boot map | 5 |
| `chat-client` loses `chat.mcp` | 5 |
| `client-init` registers every Boot entry, reconciles agent rows, `--clientpath` collision | 6 |
| `client.json` defect filed | 6 |
| Two-process relay with two agents, unlisted 401 | 7 |
| Documents, drift relink, register | 8 |
| `build-health.sh --ci` | 9 |

## Deviation From the Spec

The spec names a single-process REST owner row test in `chat-deploy-memory`. That module's test classpath holds no `chat-webflux`, because its `chat-deploy` dependency excludes it, and its `rest-core-e2e` jar holds none either. Adding `chat-webflux` there changes the core jar of the relay test. Task 3 measures the REST selection through the production `AgentSecurityConfiguration` beans instead. The owner row on a single-process REST launch is written from the same principal that this test reads.

The spec names a REST principal assertion in the relay test. On the relay path, REST forwards the token and keeps no observable trace of its own principal. So Task 3 owns that assertion, and the relay test asserts the owner rows alone.
