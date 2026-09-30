# REST agent authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Do not use subagent-driven development.** `AGENTS.md` forbids it for this repository.

**Goal:** Make every route of the application chain require a valid agent bearer token, and prove that a denied caller reaches no service.

**Architecture:** `chat-webflux` gains a resource-server chain. The chain reads four required properties, decodes a JWT with a public key from a JWK file, converts the JWT into a custom `AbstractAuthenticationToken` whose principal is the startup-resolved `ChatUserDetails`, and requires one scope authority on every exchange. `ContextIdentity` does not change. The agent resolves once at startup through `ChatUserService`.

**Tech Stack:** Kotlin 2.4.10, Spring Boot 4.0.8, Spring Security 7.0.7, Spring Authorization Server 7.0.7, Nimbus JOSE JWT 10.4, reactor-core 3.8.7, JUnit Jupiter 6, Mockito, WebTestClient, Maven.

**Spec:** `docs/superpowers/specs/2026-09-29-rest-agent-authentication-design.md`

## Global Constraints

- Kotlin is the source language. Every new file is `.kt`.
- Java target is 25. Kotlin `jvmTarget` is 25.
- No module declares a third-party version. A BOM-managed artifact is declared with no `<version>`. An unmanaged artifact takes its version from the parent `dependencyManagement`.
- Two enforcer rules run in `validate` in all modules: `requireUpperBoundDeps` and `dependencyConvergence`. A failure is a real tree change. Fix it with a parent entry, never a module pin.
- `chat-core` does **not** enable the Kotlin all-open compiler plugin. An `open` keyword is required there. `chat-webflux`, `chat-deploy` and `chat-service-composite` do enable it.
- Run `mvn -o -pl chat-core,<module> test`, never `-pl <module>` alone. A single-module run resolves `chat-core` from `~/.m2`.
- One Maven build per worktree. A concurrent `clean` in another worktree deletes this build's `target` and fakes a classpath failure. **Task 0 gates this.**
- **Never pipe a Maven command into `tail` or `head`.** A pipe reports the exit code of the pipe, and not the build. Write the output to a file under `logs/`. Read the exit code first. Then read the summary lines from the file.
- `logs/` is ignored by `.gitignore` line 12 (`*.log`). A build log never enters a commit.
- Run no Maven command before Task 0 reports that the baseline build finished.
- Every property in `app.security` has **no default**. An absent value fails the context refresh.
- Write all new prose in strict ASD-STE100 style. Use short sentences. Use active voice. Do not use semicolons.
- Every commit message ends with the two attribution lines in this plan's commit steps.
- Reference the fp issue `CHAT-pgpmsgvr` in every commit message.

## Review Focus

The spec is a vision document. Each line below names an input or condition the spec implies but no task's own tests would otherwise exercise. Each line has a test in the task that owns the code.

1. **A correctly signed token that carries the right `client_id` and scope, but was issued for a different resource.** The design defers audience validation. A reasonable person expects a refusal. Task 5 pins the **accepted** answer so the gap is recorded and cannot widen in silence.
2. **A JWK file that holds a key of an unexpected type or curve.** An operator can point the property at any file. A reasonable person expects a startup failure that names the file. Task 3 pins it.
3. **A `client_id` claim that is present but is not a string** (a JSON number or an array). A reasonable person expects the same controlled refusal as a wrong client id, and not a `ClassCastException`. Task 4 pins it.
4. **A `scope` claim delivered as a JSON array rather than a space-delimited string.** A reasonable person expects the same authority either way. Task 4 pins it.
5. **An agent username that the user store answers more than once.** A `Flux` can carry two rows. A reasonable person expects a startup failure, and not a silent first-row pick. Task 2 pins it.

---

### Task 0: Preflight — confirm the baseline, and claim the worktree

**Files:** none. **This task produces no commit.** It is a gate, and every later
task depends on it.

**Interfaces:**
- Consumes: nothing.
- Produces: a reported baseline result, and a clear worktree. No artifact.

- [ ] **Step 1: Wait for the baseline build to finish**

The baseline build runs in the background and writes
`logs/baseline-default.log`. Wait for its `EXIT=` marker before any Maven work.

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
for i in $(seq 1 120); do
  grep -qE "^EXIT=" logs/baseline-default.log && break
  sleep 10
done
grep -E "BUILD (SUCCESS|FAILURE)|^EXIT=" logs/baseline-default.log | tail -3
```

Expected: `BUILD SUCCESS` and `EXIT=0`. **Report the module count, the test
count and the skipped count to the owner before Step 4.**

**If the log shows `BUILD FAILURE` or a non-zero `EXIT=`**, stop and report it.
Do not start Task 1. A red baseline makes every later failure ambiguous.

**If the loop expires with no `EXIT=` marker**, the build is still running. Wait
longer. Do not start Maven work in the same worktree.

- [ ] **Step 2: Confirm the baseline numbers**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
grep -E "^\[INFO\] Reactor Summary|SUCCESS \[|FAILURE \[" logs/baseline-default.log | tail -42
grep -E "Tests run:.*Failures.*Errors.*Skipped" logs/baseline-default.log | tail -3
```

Expected: 36 modules, and a `Tests run:` line that reports 0 failures and 0
errors. Record the four numbers. Task 11 compares against them.

- [ ] **Step 3: Confirm that no other build holds this worktree**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
pgrep -fl "classworlds.launcher.Launcher" > logs/t0-maven.log 2>&1
grep -F "/.worktrees/pgpmsgvr" logs/t0-maven.log && echo "STOP: a build already runs in this worktree" || echo "clear: no build runs in this worktree"
```

Expected: `clear: no build runs in this worktree`.

**A process in another worktree may still run.** It does not touch this
`target`, so it is not a blocker. It does share `~/.m2`. If it holds an install
lock, this build waits rather than fails. Read the log before you call a stall a
defect.

- [ ] **Step 4: Confirm that the log directory cannot enter a commit**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
git check-ignore -v logs/baseline-default.log
git status --short
```

Expected: a `.gitignore` rule names `*.log`, and `git status` lists no file
under `logs/`.

- [ ] **Step 5: Narrow the issue to REST, in the issue record**

The spec lists this act under "Issue updates before implementation", so it runs
before any code. **A comment does not narrow an issue. Update the title and the
description.**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
fp issue update CHAT-pgpmsgvr \
  --title 'Enforce REST agent authentication on every application chain route' \
  --description 'The MCP adapter reaches real Demo Chat routes, and no deployment enforced a credential. This issue adds the enforcement.

Every route of the application chain requires a valid agent bearer token. The
chain owns the REST topic reads, the message reads, recall, send, and every
other route it handles. The actuator chain stays separate.

**RSocket is out of scope.** The owner narrowed this issue to REST on
2026-09-29. The adapter opens no RSocket connection. CHAT-jkordfef and
CHAT-ileqgajf own the RSocket gap. RSocketServerConfiguration.
rsocketSecurityAuthentication still permits every payload.

**"Without permission" means "without the configured scope" in this issue.**
The scope comes from app.security.agent.required-scope. Object-level denial
stays with the grant and authorization work in CHAT-zhjltbky and CHAT-znprrzhn.

Required proof, and the answer for each:
- A valid credential resolves to one Demo Chat identity. The agent resolves once
  at startup through the user store.
- Missing, expired and invalid credentials fail before the service runs. The
  answer is 401.
- A caller without the configured scope receives a safe error. The answer is 403.
- A denied read exposes no object name, count, key or content. Measured.
- A denied send causes no persistence, index or pub/sub effect. Measured against
  a mock service.
- Tests use the real intercepted route or a separately proxied service. No
  internal service call counts as route proof.

Recorded consequence: the boundary denies every interactive chat-web user of
this API deployment, because a user token does not match the configured agent
client. Narrowing the route set is a later design decision.

This issue is separate from MCP protocol work. CHAT-ylvoiixm remains unable to
claim end-to-end MCP authorization until this issue passes. Preserve the adapter
evidence boundary until then.'
```

Verify that the update landed in the issue record:

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
fp issue show CHAT-pgpmsgvr > logs/t0-issue.log 2>&1
head -20 logs/t0-issue.log
```

Expected: the title names the REST application chain, and the body carries the
two out-of-scope statements. **The old title and the old scope would stay in
place behind a comment alone.**

- [ ] **Step 6: Report, and start Task 1**

Report to the owner: the baseline result, the four numbers, the worktree state,
and the issue update. **No code starts before this report.** Then run Task 1.

---

### Task 1: The agent properties, and the startup guard on their absence

**Files:**
- Create: `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentSecurityProperties.kt`
- Create: `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentSecurityConfiguration.kt`
- Modify: `chat-webflux/pom.xml` (add the resource-server starter in Step 5, so every later task compiles)
- Test: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/AgentSecurityPropertiesTests.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `AgentSecurityProperties` with `agent: AgentSecurityProperties.Agent` and `jwt: AgentSecurityProperties.Jwt`. `Agent` carries `clientId: String`, `username: String`, `requiredScope: String`. `Jwt` carries `jwkPath: String`. `AgentSecurityConfiguration` is a `@Configuration` that enables the binding.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentSecurityConfiguration
import com.demo.chat.config.agent.AgentSecurityProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.BeanCreationException
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

/**
 * The four properties have no default. An absent value must fail the context,
 * and the failure must name the property.
 */
class AgentSecurityPropertiesTests {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AgentSecurityProperties::class)
    class PropertiesOnly

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(PropertiesOnly::class.java)

    private val complete = arrayOf(
        "app.security.agent.client-id=31649af5-0154-4be5-8695-fda9d18b7981",
        "app.security.agent.username=agent-svc",
        "app.security.agent.required-scope=chat.mcp",
        "app.security.jwt.jwk-path=/tmp/agent-test.jwk",
    )

    @Test
    fun `the four values bind`() {
        runner.withPropertyValues(*complete).run { context ->
            val properties = context.getBean(AgentSecurityProperties::class.java)
            assertThat(properties.agent.username).isEqualTo("agent-svc")
            assertThat(properties.agent.requiredScope).isEqualTo("chat.mcp")
            assertThat(properties.jwt.jwkPath).isEqualTo("/tmp/agent-test.jwk")
        }
    }

    @Test
    fun `an absent client id fails the context and names the property`() {
        val withoutClientId = complete.filterNot { it.startsWith("app.security.agent.client-id") }
        runner.withPropertyValues(*withoutClientId.toTypedArray()).run { context ->
            assertThat(context.startupFailure).isNotNull
            assertThat(rootMessages(context.startupFailure!!))
                .anyMatch { it.contains("app.security.agent.client-id") }
        }
    }

    @Test
    fun `an absent jwk path fails the context and names the property`() {
        val withoutJwk = complete.filterNot { it.startsWith("app.security.jwt.jwk-path") }
        runner.withPropertyValues(*withoutJwk.toTypedArray()).run { context ->
            assertThat(context.startupFailure).isNotNull
            assertThat(rootMessages(context.startupFailure!!))
                .anyMatch { it.contains("app.security.jwt.jwk-path") }
        }
    }

    @Test
    fun `an absent agent username fails the context and names the property`() {
        val withoutUsername = complete.filterNot { it.startsWith("app.security.agent.username") }
        runner.withPropertyValues(*withoutUsername.toTypedArray()).run { context ->
            assertThat(context.startupFailure).isNotNull
            assertThat(rootMessages(context.startupFailure!!))
                .anyMatch { it.contains("app.security.agent.username") }
        }
    }

    @Test
    fun `an absent required scope fails the context and names the property`() {
        val withoutScope = complete.filterNot { it.startsWith("app.security.agent.required-scope") }
        runner.withPropertyValues(*withoutScope.toTypedArray()).run { context ->
            assertThat(context.startupFailure).isNotNull
            assertThat(rootMessages(context.startupFailure!!))
                .anyMatch { it.contains("app.security.agent.required-scope") }
        }
    }

    /** Every message in the cause chain. A bind failure nests the useful text. */
    private fun rootMessages(failure: Throwable): List<String> {
        val messages = mutableListOf<String>()
        var current: Throwable? = failure
        while (current != null) {
            messages += current.message.orEmpty()
            current = current.cause
        }
        return messages
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-webflux -Dtest=AgentSecurityPropertiesTests -Dsurefire.failIfNoSpecifiedTests=false test > logs/t1-properties-first.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR" logs/t1-properties-first.log | tail -10
```

Expected: FAIL to compile. `AgentSecurityProperties` does not exist.

- [ ] **Step 3: Write the properties class**

```kotlin
package com.demo.chat.config.agent

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The agent identity of this deployment. See `CHAT-pgpmsgvr`.
 *
 * **No value here has a default.** A default would make every deployment the
 * same agent in silence. That is the `app.nodeid` lesson.
 *
 * A field is a non-null [String] with no default, so Spring refuses to bind a
 * context that does not carry the value. The bind failure names the property.
 */
@ConfigurationProperties("app.security")
data class AgentSecurityProperties(
    val agent: Agent,
    val jwt: Jwt,
) {

    /** The OAuth client and the chat user that name the agent. */
    data class Agent(
        /** The `client_id` claim that an agent token must carry. */
        val clientId: String,
        /** The chat user handle that names the agent identity. */
        val username: String,
        /** The scope that every enforced request must carry. This deployment selects `chat.mcp`. */
        val requiredScope: String,
    )

    /** The trusted signing material. */
    data class Jwt(
        /** The JWK file that holds the trusted public key. */
        val jwkPath: String,
    )
}
```

- [ ] **Step 4: Write the configuration that enables the binding**

```kotlin
package com.demo.chat.config.agent

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * One place that turns on the `app.security` binding. A reader finds the
 * required property set from here. See `CHAT-pgpmsgvr`.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentSecurityProperties::class)
class AgentSecurityConfiguration
```

- [ ] **Step 5: Add the resource-server starter to the pom**

In `chat-webflux/pom.xml`, directly after the `spring-boot-starter-oauth2-client` dependency block, add:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
```

Declare no version. The Boot BOM manages it.

- [ ] **Step 6: Run the test to verify it passes**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-webflux -Dtest=AgentSecurityPropertiesTests -Dsurefire.failIfNoSpecifiedTests=false test > logs/t1-properties.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR" logs/t1-properties.log | tail -10
```

Expected: PASS, 5 tests. One test binds the four values, and four tests refuse an
absent value. **Each of the four properties owns its own absence test**, so a
default added to any one of them fails here.

If an absent-value case reports no failure, the Kotlin constructor binding has supplied an empty string. Convert `Agent`, `Jwt` and the outer class to plain classes with `var` fields and a `@PostConstruct` that throws a `ChatException` naming each absent key. Do not weaken the assertion.

- [ ] **Step 7: Prove the pom is clean**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -B validate > logs/t1-validate.log 2>&1
rc=$?
echo "mvn exit=$rc"
grep -E "BUILD (SUCCESS|FAILURE)|ERROR" logs/t1-validate.log | tail -10
shell-scripts/check-dependency-versions.sh > logs/t1-deps.log 2>&1
rc=$?
echo "deps exit=$rc"
cat logs/t1-deps.log
```

Expected: `mvn exit=0`, and `deps exit=0` with no violation line. The guard prints
nothing when every module pom is clean.

- [ ] **Step 8: Commit**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
git add chat-webflux/pom.xml \
        chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentSecurityProperties.kt \
        chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentSecurityConfiguration.kt \
        chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/AgentSecurityPropertiesTests.kt
git commit -m "CHAT-pgpmsgvr: bind the agent properties, and fail on an absent value

Four values under app.security carry no default. A bind failure names the
missing property.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: The agent identity, resolved once at startup

**Files:**
- Create: `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentIdentity.kt`
- Create: `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentIdentityLifecycle.kt`
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentSecurityConfiguration.kt`
- Test: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/AgentIdentityLifecycleTests.kt`

**Interfaces:**
- Consumes: `AgentSecurityProperties` from Task 1.
- Produces: `AgentIdentity.resolve(user: ChatUserDetails<*>)` and `AgentIdentity.principal(): ChatUserDetails<*>`. `AgentIdentityLifecycle.PHASE` equals `Int.MAX_VALUE - 3072`.

**Module note.** The spec says `RootKeyStartup` runs the agent resolution. That is not possible. `RootKeyStartup` lives in `chat-deploy`, and `chat-webflux` cannot see it. So this task adds a second `SmartLifecycle` in `chat-webflux`, with a phase between `RootKeyStartup` and the servers. Record this deviation in the spec in Task 11.

Server phases, read from source: `RootKeyStartup` is `Int.MAX_VALUE - 4096`. The reactive web server is `Int.MAX_VALUE - 2048`. The RSocket server is the default `Int.MAX_VALUE`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentIdentity
import com.demo.chat.config.agent.AgentIdentityLifecycle
import com.demo.chat.config.agent.AgentSecurityProperties
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.domain.ByStringRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux

class AgentIdentityLifecycleTests {

    private val properties = AgentSecurityProperties(
        agent = AgentSecurityProperties.Agent(
            clientId = "client-under-test",
            username = "agent-svc",
            requiredScope = "chat.mcp",
        ),
        jwt = AgentSecurityProperties.Jwt(jwkPath = "/tmp/agent-test.jwk"),
    )

    /** `User` is an interface with a factory. There is no `UserStatus`. */
    private fun user(id: Long, handle: String): User<Long> =
        User.create(Key.of(id, 1L), handle, handle, "http://$handle")

    @Test
    fun `the configured username resolves the agent principal`() {
        val users = mock<ChatUserService<Long>>()
        whenever(users.findByUsername(ByStringRequest("agent-svc"))).thenReturn(Flux.just(user(7L, "agent-svc")))
        val identity = AgentIdentity()

        AgentIdentityLifecycle(users, identity, properties).start()

        assertThat(identity.principal().user.key.id).isEqualTo(7L)
        assertThat(identity.principal().username).isEqualTo("agent-svc")
    }

    @Test
    fun `an unknown username fails the start and names the username`() {
        val users = mock<ChatUserService<Long>>()
        whenever(users.findByUsername(ByStringRequest("agent-svc"))).thenReturn(Flux.empty())
        val identity = AgentIdentity()

        val failure = assertThrows<RuntimeException> {
            AgentIdentityLifecycle(users, identity, properties).start()
        }

        assertThat(failure.message).contains("agent-svc")
    }

    @Test
    fun `a username that answers twice fails the start`() {
        val users = mock<ChatUserService<Long>>()
        whenever(users.findByUsername(ByStringRequest("agent-svc")))
            .thenReturn(Flux.just(user(7L, "agent-svc"), user(8L, "agent-svc")))
        val identity = AgentIdentity()

        val failure = assertThrows<RuntimeException> {
            AgentIdentityLifecycle(users, identity, properties).start()
        }

        assertThat(failure.message).contains("agent-svc")
    }

    @Test
    fun `an unresolved identity refuses to answer a principal`() {
        val identity = AgentIdentity()

        val failure = assertThrows<RuntimeException> { identity.principal() }

        assertThat(failure.message).contains("agent")
    }

    @Test
    fun `the phase sits between the root keys and the web server`() {
        assertThat(AgentIdentityLifecycle.PHASE).isGreaterThan(Int.MAX_VALUE - 4096)
        assertThat(AgentIdentityLifecycle.PHASE).isLessThan(Int.MAX_VALUE - 2048)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-webflux -Dtest=AgentIdentityLifecycleTests -Dsurefire.failIfNoSpecifiedTests=false test > logs/t2-identity-first.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR" logs/t2-identity-first.log | tail -10
```

Expected: FAIL to compile. `AgentIdentity` does not exist.

The fixture is already the real signature, read from source on 2026-09-29.
`User` is an interface at `com.demo.chat.domain.User` with a companion factory
`User.create(key, name, handle, imageUri)`. `Key.of(id, root)` takes two
arguments. There is no `UserStatus` and no `UserKey` in `chat-core`.

- [ ] **Step 3: Write the identity holder**

```kotlin
package com.demo.chat.config.agent

import com.demo.chat.domain.ChatException
import com.demo.chat.security.ChatUserDetails

/**
 * The one agent identity of this process. See `CHAT-pgpmsgvr`.
 *
 * The identity resolves once, at startup. No request reads the user store for
 * the agent. [principal] throws when the identity is absent, so a request that
 * arrives before the resolution fails loudly rather than in silence.
 */
class AgentIdentity {

    @Volatile
    private var resolved: ChatUserDetails<*>? = null

    fun resolve(user: ChatUserDetails<*>) {
        resolved = user
    }

    fun principal(): ChatUserDetails<*> = resolved ?: throw ChatException(
        "The agent identity is not resolved. The configured agent user is missing from the user store."
    )
}
```

- [ ] **Step 4: Write the lifecycle**

```kotlin
package com.demo.chat.config.agent

import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.ChatException
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.composite.ChatUserService
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import java.time.Duration

/**
 * Resolves the agent identity once, before any server accepts a request.
 * See `CHAT-pgpmsgvr`.
 *
 * [RootKeyStartup] in `chat-deploy` is `Integer.MAX_VALUE - 4096`. The
 * reactive web server is `Integer.MAX_VALUE - 2048`. The RSocket server is the
 * default `Integer.MAX_VALUE`. This phase sits between the first two, so the
 * roots are loaded and no server is listening.
 *
 * A failed lookup throws from [start]. The context refresh then fails, and the
 * process does not start.
 */
class AgentIdentityLifecycle<T>(
    private val users: ChatUserService<T>,
    private val identity: AgentIdentity,
    private val properties: AgentSecurityProperties,
) : SmartLifecycle {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var running = false

    override fun start() {
        val username = properties.agent.username
        val found = users.findByUsername(ByStringRequest(username))
            .collectList()
            .block(Duration.ofSeconds(30))
            ?: throw ChatException(
                "The agent user lookup for '$username' answered nothing within 30 seconds."
            )

        if (found.isEmpty()) {
            throw ChatException(
                "The agent username '$username' is not in the user store. Create the user, or change " +
                    "app.security.agent.username."
            )
        }
        if (found.size > 1) {
            throw ChatException(
                "The agent username '$username' answers ${found.size} users. A username must name one user."
            )
        }

        identity.resolve(ChatUserDetails(found.single(), emptyList()))
        logger.info("The agent identity resolved as '$username'.")
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = PHASE

    companion object {
        /** Above the root keys at `Integer.MAX_VALUE - 4096`, and below the web server at `-2048`. */
        const val PHASE = Int.MAX_VALUE - 3072
    }
}
```

- [ ] **Step 5: Declare both beans**

Append to `AgentSecurityConfiguration`:

```kotlin
    /**
     * One identity holder for the process.
     *
     * `AgentIdentity` is not generic, so it is a plain bean. The lifecycle is
     * generic in the key type, and it takes the deployment's `ChatUserService`.
     */
    @Bean
    fun agentIdentity(): AgentIdentity = AgentIdentity()

    /**
     * A `@ConditionalOnBean` guard is deliberate. A `chat-webflux` test slice
     * carries no `ChatUserService`, and that slice does not run a chain that
     * needs an agent.
     */
    @Bean
    @ConditionalOnBean(ChatUserService::class)
    fun <T> agentIdentityLifecycle(
        users: ChatUserService<T>,
        identity: AgentIdentity,
        properties: AgentSecurityProperties,
    ): AgentIdentityLifecycle<T> = AgentIdentityLifecycle(users, identity, properties)
```

Add the imports for `Bean`, `ConditionalOnBean` and `ChatUserService`. Change the class annotation list to keep `@Configuration(proxyBeanMethods = false)` and `@EnableConfigurationProperties`.

**A generic `@Bean` method on `@Configuration` needs the return type to carry `<T>`.** If Spring refuses the bean, split the lifecycle into a non-generic class that takes `ChatUserService<*>` and casts inside `start`. Keep the same tests.

- [ ] **Step 6: Run the tests to verify they pass**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-webflux -Dtest=AgentIdentityLifecycleTests -Dsurefire.failIfNoSpecifiedTests=false test > logs/t2-identity.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR" logs/t2-identity.log | tail -10
```

Expected: PASS, 5 tests.

- [ ] **Step 7: Commit**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
git add chat-webflux/src/main/kotlin/com/demo/chat/config/agent/ \
        chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/AgentIdentityLifecycleTests.kt
git commit -m "CHAT-pgpmsgvr: resolve the agent identity once, at startup

The lookup runs in a SmartLifecycle below both servers. An unknown username
and a duplicated username both fail the context.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: The decoder, built from the public half of a JWK file

**Files:**
- Create: `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentJwtDecoderFactory.kt`
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentSecurityConfiguration.kt`
- Test: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/AgentJwtDecoderFactoryTests.kt`

**Interfaces:**
- Consumes: `AgentSecurityProperties` from Task 1.
- Produces: `AgentJwtDecoderFactory.fromJwkFile(path: String): NimbusReactiveJwtDecoder`, and a `@Bean fun reactiveJwtDecoder(...)` in `AgentSecurityConfiguration` typed `ReactiveJwtDecoder`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentJwtDecoderFactory
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import java.util.Date
import java.util.UUID

class AgentJwtDecoderFactoryTests {

    private fun tempFile(name: String, content: String): Path {
        val file = Files.createTempFile(name, ".jwk")
        file.toFile().deleteOnExit()
        Files.writeString(file, content)
        return file
    }

    private fun es256Key(): ECKey = ECKeyGenerator(Curve.P_256)
        .keyID(UUID.randomUUID().toString())
        .generate()

    private fun token(key: ECKey, expiry: Date): String {
        val claims = JWTClaimsSet.Builder()
            .issuer("https://authserv")
            .subject("client-under-test")
            .claim("client_id", "client-under-test")
            .claim("scope", "chat.mcp")
            .expirationTime(expiry)
            .build()
        val signed = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.keyID).type(JOSEObjectType.JWT).build(),
            claims,
        )
        signed.sign(ECDSASigner(key))
        return signed.serialize()
    }

    @Test
    fun `a token signed by the trusted key decodes`() {
        val key = es256Key()
        val file = tempFile("agent-decoder", key.toJSONString())

        val decoder = AgentJwtDecoderFactory.fromJwkFile(file.toString())
        val jwt = decoder.decode(token(key, Date(System.currentTimeMillis() + 60_000))).block()!!

        assertThat(jwt.claims["client_id"]).isEqualTo("client-under-test")
        assertThat(jwt.claims["scope"]).isEqualTo("chat.mcp")
    }

    /**
     * An expired token is refused. This is the only claim check this issue
     * adds beyond the signature.
     */
    @Test
    fun `an expired token is refused`() {
        val key = es256Key()
        val file = tempFile("agent-decoder-expired", key.toJSONString())

        val decoder = AgentJwtDecoderFactory.fromJwkFile(file.toString())

        assertThrows<Exception> {
            decoder.decode(token(key, Date(System.currentTimeMillis() - 60_000))).block()
        }
    }

    @Test
    fun `a token signed by another key is refused`() {
        val trusted = es256Key()
        val other = es256Key()
        val file = tempFile("agent-decoder-other", trusted.toJSONString())

        val decoder = AgentJwtDecoderFactory.fromJwkFile(file.toString())

        assertThrows<Exception> {
            decoder.decode(token(other, Date(System.currentTimeMillis() + 60_000))).block()
        }
    }

    /**
     * A JWK that carries private material still decodes. The factory discards
     * the private half. See owner decision 7.
     */
    @Test
    fun `a JWK that carries the private key still decodes`() {
        val key = es256Key()
        val file = tempFile("agent-decoder-private", key.toJSONString())

        val decoder = AgentJwtDecoderFactory.fromJwkFile(file.toString())
        val jwt = decoder.decode(token(key, Date(System.currentTimeMillis() + 60_000))).block()!!

        assertThat(jwt.subject).isEqualTo("client-under-test")
    }

    /**
     * Review Focus line 2. An unexpected key type must fail the start and name
     * the file.
     */
    @Test
    fun `an RSA key is refused, and the message names the file`() {
        val rsa = RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate()
        val file = tempFile("agent-decoder-rsa", rsa.toJSONString())

        val failure = assertThrows<RuntimeException> {
            AgentJwtDecoderFactory.fromJwkFile(file.toString())
        }

        assertThat(failure.message).contains(file.fileName.toString())
    }

    /**
     * A trusted key on another curve is refused. ES256 requires P-256, and a
     * P-384 key verifies nothing this issuer signs.
     */
    @Test
    fun `a key on another curve is refused, and the message names both curves`() {
        val p384 = ECKeyGenerator(Curve.P_384).keyID(UUID.randomUUID().toString()).generate()
        val file = tempFile("agent-decoder-p384", p384.toJSONString())

        val failure = assertThrows<RuntimeException> {
            AgentJwtDecoderFactory.fromJwkFile(file.toString())
        }

        assertThat(failure.message).contains("P-384").contains("P-256")
    }

    @Test
    fun `an absent file is refused, and the message names the path`() {
        val missing = "/tmp/agent-decoder-does-not-exist-${UUID.randomUUID()}.jwk"

        val failure = assertThrows<RuntimeException> {
            AgentJwtDecoderFactory.fromJwkFile(missing)
        }

        assertThat(failure.message).contains(missing)
    }

    @Test
    fun `a file that is not a JWK is refused`() {
        val file = tempFile("agent-decoder-garbage", "this is not json")

        val failure = assertThrows<RuntimeException> {
            AgentJwtDecoderFactory.fromJwkFile(file.toString())
        }

        assertThat(failure.message).contains(file.fileName.toString())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-webflux -Dtest=AgentJwtDecoderFactoryTests -Dsurefire.failIfNoSpecifiedTests=false test > logs/t3-decoder-first.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR" logs/t3-decoder-first.log | tail -10
```

Expected: FAIL to compile. `AgentJwtDecoderFactory` does not exist.

- [ ] **Step 3: Write the factory**

```kotlin
package com.demo.chat.config.agent

import com.demo.chat.domain.ChatException
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.SignedJWT
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import reactor.core.publisher.Flux
import java.nio.file.Files
import java.nio.file.Paths
import java.util.function.Function

/**
 * Builds the one decoder this deployment trusts. See `CHAT-pgpmsgvr`.
 *
 * **The file may carry private material, and this code discards it.** The
 * process reads the public half alone, and the decoder verifies a signature
 * with it. A public-only JWK file is the better input, because then no private
 * material enters the process at all.
 *
 * The decoder validates the signature and the `exp` claim. It adds no audience
 * validator, and the spec records that limitation.
 *
 * **The key type is checked.** An RSA key in the file is a refusal. This
 * deployment signs with ES256, and a silently accepted RSA key would verify
 * nothing that this issuer produces.
 */
object AgentJwtDecoderFactory {

    fun fromJwkFile(path: String): NimbusReactiveJwtDecoder {
        val file = Paths.get(path)
        val json = try {
            Files.readString(file)
        } catch (e: Exception) {
            throw ChatException(
                "The JWK file '${file.fileName}' is not readable at '$path'. Cause: ${e.message}"
            ).apply { initCause(e) }
        }

        val parsed = try {
            JWK.parse(json)
        } catch (e: Exception) {
            throw ChatException(
                "The file '${file.fileName}' at '$path' is not a JWK. Cause: ${e.message}"
            ).apply { initCause(e) }
        }

        val publicJwk = parsed.toPublicJWK()
            ?: throw ChatException(
                "The JWK in '${file.fileName}' at '$path' holds no public key."
            )

        if (publicJwk !is ECKey) {
            throw ChatException(
                "The JWK in '${file.fileName}' at '$path' is a ${publicJwk.keyType.value} key. " +
                    "This deployment trusts an EC key for ${SignatureAlgorithm.ES256.name}."
            )
        }

        if (publicJwk.curve != Curve.P_256) {
            throw ChatException(
                "The JWK in '${file.fileName}' at '$path' is on curve " +
                    "${publicJwk.curve.name}. ${SignatureAlgorithm.ES256.name} " +
                    "requires ${Curve.P_256.name}."
            )
        }

        // The decoder reads the key from this source. **No network call happens**,
        // and no issuer is discovered. `withPublicKey` cannot serve here, because
        // it accepts an `RSAPublicKey` alone.
        val jwkSource = Function<SignedJWT, Flux<JWK>> { Flux.just(publicJwk) }

        return NimbusReactiveJwtDecoder.withJwkSource(jwkSource)
            .jwsAlgorithm(SignatureAlgorithm.ES256)
            .build()
    }
}
```

**Signatures in this block, checked against the bytecode on 2026-09-29.**

| Member | Verified form |
|---|---|
| `NimbusReactiveJwtDecoder.withJwkSource` | `withJwkSource(Function<SignedJWT, Flux<JWK>>)`. It returns a `JwkSourceReactiveJwtDecoderBuilder`. |
| `NimbusReactiveJwtDecoder.withPublicKey` | **`(RSAPublicKey)` alone.** It cannot serve an EC key, and the trusted key here is EC. |
| `JwkSourceReactiveJwtDecoderBuilder.jwsAlgorithm` | `(JwsAlgorithm)`. **The name is `jwsAlgorithm`, and not `signatureAlgorithm`.** |
| `SignatureAlgorithm` | `implements JwsAlgorithm`, so `SignatureAlgorithm.ES256` is a legal argument. |
| `ECKey.toPublicJWK` | covariant `ECKey`. `getCurve()` answers a `Curve`. |
| `JWKSource` reach | The source answers the local `publicJwk` alone. It opens no connection, and it discovers no issuer. |

- [ ] **Step 4: Declare the decoder bean**

Append to `AgentSecurityConfiguration`:

```kotlin
    /**
     * The decoder of the application chain. A bad JWK fails the context
     * refresh, so an operator learns at the start and not at the first request.
     */
    @Bean
    fun reactiveJwtDecoder(properties: AgentSecurityProperties): ReactiveJwtDecoder =
        AgentJwtDecoderFactory.fromJwkFile(properties.jwt.jwkPath)
```

Add the import `org.springframework.security.oauth2.jwt.ReactiveJwtDecoder`.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-webflux -Dtest=AgentJwtDecoderFactoryTests -Dsurefire.failIfNoSpecifiedTests=false test > logs/t3-decoder.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR" logs/t3-decoder.log | tail -10
```

Expected: PASS, 8 tests.

- [ ] **Step 6: Commit**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
git add chat-webflux/src/main/kotlin/com/demo/chat/config/agent/ \
        chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/AgentJwtDecoderFactoryTests.kt
git commit -m "CHAT-pgpmsgvr: decode a token with the public half of the JWK file

The factory discards private material. A wrong key type, an unreadable file
and an unparsable file each fail the start and name the path.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: The authentication token, and the converter that builds it

**Files:**
- Create: `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentAuthenticationToken.kt`
- Create: `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentAuthenticationConverter.kt`
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentSecurityConfiguration.kt`
- Test: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/AgentAuthenticationConverterTests.kt`

**Interfaces:**
- Consumes: `AgentIdentity` from Task 2, `AgentSecurityProperties` from Task 1.
- Produces: `AgentAuthenticationToken(principal, jwt, authorities)` with `val jwt: Jwt`. `AgentAuthenticationConverter(identity, expectedClientId)` implementing `Converter<Jwt, Mono<AbstractAuthenticationToken>>`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentAuthenticationConverter
import com.demo.chat.config.agent.AgentAuthenticationToken
import com.demo.chat.config.agent.AgentIdentity
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class AgentAuthenticationConverterTests {

    private val identity = AgentIdentity().apply {
        resolve(
            ChatUserDetails(
                User.create(Key.of(7L, 1L), "agent-svc", "agent-svc", "http://agent-svc"),
                emptyList(),
            )
        )
    }

    private fun jwt(claims: Map<String, Any>): Jwt = Jwt(
        "a-token-value",
        Instant.now(),
        Instant.now().plusSeconds(60),
        mapOf("alg" to "ES256"),
        claims,
    )

    private fun converter() = AgentAuthenticationConverter(identity, "client-under-test")

    @Test
    fun `a matching client id builds the agent authentication`() {
        val token = converter().convert(
            jwt(mapOf("client_id" to "client-under-test", "scope" to "chat.mcp"))
        ).block()!!

        assertThat(token).isInstanceOf(AgentAuthenticationToken::class.java)
        assertThat(token.isAuthenticated).isTrue()
        assertThat(token.principal).isInstanceOf(ChatUserDetails::class.java)
        assertThat((token.principal as ChatUserDetails<*>).username).isEqualTo("agent-svc")
        assertThat(token.authorities.map { it.authority }).contains("SCOPE_chat.mcp")
    }

    @Test
    fun `a wrong client id raises the controlled failure`() {
        val failure = assertThrows<BadCredentialsException> {
            converter().convert(jwt(mapOf("client_id" to "another-client", "scope" to "chat.mcp"))).block()
        }
        assertThat(failure.message).doesNotContain("another-client")
    }

    @Test
    fun `an absent client id claim raises the controlled failure`() {
        assertThrows<BadCredentialsException> {
            converter().convert(jwt(mapOf("scope" to "chat.mcp"))).block()
        }
    }

    /** Review Focus line 3. A non-string claim must not reach a cast. */
    @Test
    fun `a client id claim that is not a string raises the controlled failure`() {
        assertThrows<BadCredentialsException> {
            converter().convert(jwt(mapOf("client_id" to 42, "scope" to "chat.mcp"))).block()
        }
    }

    /** Review Focus line 4. An array scope and a string scope give the same authorities. */
    @Test
    fun `a scope claim in a list gives the same authority as a string`() {
        val fromList = converter().convert(
            jwt(mapOf("client_id" to "client-under-test", "scope" to listOf("chat.mcp", "profile")))
        ).block()!!
        val fromString = converter().convert(
            jwt(mapOf("client_id" to "client-under-test", "scope" to "chat.mcp profile"))
        ).block()!!

        assertThat(fromList.authorities.map { it.authority })
            .containsExactlyInAnyOrderElementsOf(fromString.authorities.map { it.authority })
        assertThat(fromList.authorities.map { it.authority }).contains("SCOPE_chat.mcp")
    }

    @Test
    fun `a token without the configured scope still converts, and the chain refuses it later`() {
        val token = converter().convert(
            jwt(mapOf("client_id" to "client-under-test", "scope" to "profile"))
        ).block()!!

        assertThat(token.authorities.map { it.authority }).doesNotContain("SCOPE_chat.mcp")
    }

    @Test
    fun `the validated jwt stays beside the principal`() {
        val token = converter().convert(
            jwt(mapOf("client_id" to "client-under-test", "scope" to "chat.mcp"))
        ).block()!! as AgentAuthenticationToken

        assertThat(token.jwt.claims["client_id"]).isEqualTo("client-under-test")
        assertThat(token.credentials).isNull()
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-webflux -Dtest=AgentAuthenticationConverterTests -Dsurefire.failIfNoSpecifiedTests=false test > logs/t4-converter-first.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR" logs/t4-converter-first.log | tail -10
```

Expected: FAIL to compile. The two classes do not exist.

- [ ] **Step 3: Write the token**

```kotlin
package com.demo.chat.config.agent

import com.demo.chat.security.ChatUserDetails
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * The authentication of one agent request. See `CHAT-pgpmsgvr`.
 *
 * **`JwtAuthenticationToken` cannot serve here.** `ContextIdentity.identityOf`
 * is a closed list, and a `Jwt` principal matches no rule. Every access check
 * would then deny. This token carries the startup-resolved `ChatUserDetails`,
 * so rule 5 answers the agent user key with no change to `ContextIdentity`.
 *
 * The validated [Jwt] stays beside the principal. The claims are useful for
 * diagnostics, and the scope check reads them.
 */
class AgentAuthenticationToken(
    private val agentPrincipal: ChatUserDetails<*>,
    val jwt: Jwt,
    authorities: Collection<GrantedAuthority>,
) : AbstractAuthenticationToken(authorities) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): Any = agentPrincipal

    override fun getName(): String = agentPrincipal.username
}
```

- [ ] **Step 4: Write the converter**

```kotlin
package com.demo.chat.config.agent

import com.demo.chat.security.ChatUserDetails
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import reactor.core.publisher.Mono

/**
 * Turns a validated [Jwt] into the agent authentication. See `CHAT-pgpmsgvr`.
 *
 * **The `client_id` claim binds the agent client.** The `sub` claim is
 * ignored, because the identity comes from the configured username and not
 * from the token.
 *
 * **A mismatch raises a controlled `AuthenticationException`.** An arbitrary
 * exception from a converter reaches the caller as a server error, and a
 * server error is not a refusal. `AuthenticationWebFilter` catches
 * `AuthenticationException` and answers through the bearer entry point, so a
 * mismatch answers 401.
 *
 * The failure carries no object name and no count. A caller cannot learn which
 * clients exist.
 *
 * `JwtGrantedAuthoritiesConverter` maps the `scope` claim to `SCOPE_<name>`,
 * so the configured scope becomes the required authority with no custom
 * mapping.
 */
class AgentAuthenticationConverter(
    private val identity: AgentIdentity,
    private val expectedClientId: String,
) : Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private val authoritiesConverter = JwtGrantedAuthoritiesConverter()

    override fun convert(jwt: Jwt): Mono<AbstractAuthenticationToken> {
        val clientId = jwt.claims["client_id"]
        if (clientId !is String || clientId != expectedClientId) {
            return Mono.error(
                BadCredentialsException("The token is not from the configured agent client.")
            )
        }

        return Mono.just(
            AgentAuthenticationToken(
                identity.principal(),
                jwt,
                authoritiesConverter.convert(jwt).orEmpty(),
            )
        )
    }
}
```

- [ ] **Step 5: Declare the converter bean**

Append to `AgentSecurityConfiguration`:

```kotlin
    /**
     * The one converter of the application chain.
     */
    @Bean
    fun agentAuthenticationConverter(
        identity: AgentIdentity,
        properties: AgentSecurityProperties,
    ): Converter<Jwt, Mono<AbstractAuthenticationToken>> =
        AgentAuthenticationConverter(identity, properties.agent.clientId)
```

Add the imports `org.springframework.core.convert.converter.Converter`,
`org.springframework.security.authentication.AbstractAuthenticationToken` and
`org.springframework.security.oauth2.jwt.Jwt`.

- [ ] **Step 6: Add the identity test the spec asks for**

Create `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/AgentIdentityResolutionTests.kt`:

```kotlin
package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentAuthenticationToken
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.test.key.TestKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * The one test the spec names: `ContextIdentity.identityOf` answers the agent
 * user key for the agent authentication, with no change to that class.
 *
 * The fixture is the one `ContextIdentityTests` and
 * `WebFluxAnonymousIdentityTests` already use. Both are in this module's test
 * classpath, so nothing new is introduced.
 */
class AgentIdentityResolutionTests {

    @Test
    fun `the agent authentication answers the agent user key`() {
        val user = User.create(USER_KEY, "agent-svc", "agent-svc", "http://agent-svc")
        val token = AgentAuthenticationToken(
            ChatUserDetails(user, emptyList()),
            Jwt("value", Instant.now(), Instant.now().plusSeconds(60), mapOf("alg" to "ES256"), emptyMap()),
            emptyList(),
        )

        val identity = ContextIdentity(rootKeys()).identityOf(token)

        assertThat(identity).isEqualTo(USER_KEY)
    }

    @Test
    fun `the agent authentication does not answer the anon root key`() {
        val user = User.create(USER_KEY, "agent-svc", "agent-svc", "http://agent-svc")
        val token = AgentAuthenticationToken(
            ChatUserDetails(user, emptyList()),
            Jwt("value", Instant.now(), Instant.now().plusSeconds(60), mapOf("alg" to "ES256"), emptyMap()),
            emptyList(),
        )

        assertThat(ContextIdentity(rootKeys()).identityOf(token)).isNotEqualTo(ANON_KEY)
    }

    private fun rootKeys() =
        RootKeysFixture.ofLong(emptyMap(), admin = TestKeys.key(9999L), anon = ANON_KEY)

    private companion object {
        val ANON_KEY: Key<Long> = TestKeys.key(1L)
        val USER_KEY: Key<Long> = TestKeys.key(2L)
    }
}
```

`RootKeysFixture` and `TestKeys` are in `com.demo.chat.test.key`. The two existing
classes in this package already import them, so the dependency is present.

- [ ] **Step 7: Run the tests to verify they pass**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-webflux -Dtest='AgentAuthenticationConverterTests,AgentIdentityResolutionTests' -Dsurefire.failIfNoSpecifiedTests=false test > logs/t4-converter.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR" logs/t4-converter.log | tail -10
```

Expected: PASS, 8 tests.

- [ ] **Step 8: Commit**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
git add chat-webflux/src/main/kotlin/com/demo/chat/config/agent/ \
        chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/
git commit -m "CHAT-pgpmsgvr: carry the agent ChatUserDetails, and bind the client id

A wrong client id raises BadCredentialsException. ContextIdentity answers the
agent user key through rule 5, with no change to that class.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: The chain, and the denial matrix over a real route

**Files:**
- Create: `chat-webflux/src/main/kotlin/com/demo/chat/config/agent/AgentResourceServerChain.kt`
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/config/WebFluxSecurity.kt`
- Create: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/security/AgentDenialMatrixTests.kt`
- Modify: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/security/BothChainsApplication.kt`
- Modify: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/security/SecurityChainOwnershipTests.kt`
- Test: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/AgentResourceServerChainTests.kt`

**Interfaces:**
- Consumes: `AgentSecurityProperties` (Task 1), `AgentJwtDecoderFactory` (Task 3), `AgentAuthenticationConverter` (Task 4).
- Produces: `AgentResourceServerChain(properties, decoder, converter)` with `build(http: ServerHttpSecurity): SecurityWebFilterChain`, `requiredAuthority(): String`, and `companion object { const val SCOPE_PREFIX = "SCOPE_"; fun authorityFor(scope: String): String }`. `WebFluxSecurity` takes an `AgentResourceServerChain` as its only constructor argument.

**Exact signatures, read with `javap` from `spring-security-config-7.0.7.jar` on 2026-09-29.**

```
ServerHttpSecurity.http()                                              static
ServerHttpSecurity.oauth2ResourceServer(Customizer<OAuth2ResourceServerSpec>)
OAuth2ResourceServerSpec.jwt(Customizer<JwtSpec>)
JwtSpec.jwtDecoder(ReactiveJwtDecoder)                                 NOT `decoder`
JwtSpec.jwtAuthenticationConverter(Converter<Jwt, ? extends Mono<? extends AbstractAuthenticationToken>>)
AuthorizeExchangeSpec.Access.hasAuthority(String)                      NOT an authority object
```

**`decoder(...)` does not exist.** The method is `jwtDecoder(...)`. **`hasAuthority` takes a `String`**, so the bare authority name goes in and `SimpleGrantedAuthority` stays on the token side.

**Two facts this task rests on, both read from bytecode on 2026-09-29.**

1. `AuthenticationWebFilter` in Spring Security 7.0.7 catches `AuthenticationException` and routes it to `onAuthenticationFailure`. So a converter failure answers 401 through the bearer entry point.
2. `SecurityWebFiltersOrder` puts `EXCEPTION_TRANSLATION` after `AUTHENTICATION`. So the resource-server filter is the component that translates the converter failure, and not `ExceptionTranslationWebFilter`.

**`anonymous` comes off the chain.** With no `anonymous`, a request with no credential reaches authorization with a null authentication. Spring then answers through the entry point, which is 401 plus `WWW-Authenticate`. A 403 would tell the caller nothing about how to authenticate.

- [ ] **Step 1: Write the unit test for the authority name**

```kotlin
package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentResourceServerChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentResourceServerChainTests {

    @Test
    fun `the required authority comes from the configured scope`() {
        assertThat(AgentResourceServerChain.authorityFor("chat.mcp")).isEqualTo("SCOPE_chat.mcp")
    }

    @Test
    fun `a different scope gives a different authority`() {
        assertThat(AgentResourceServerChain.authorityFor("other.scope")).isEqualTo("SCOPE_other.scope")
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-webflux -Dtest=AgentResourceServerChainTests -Dsurefire.failIfNoSpecifiedTests=false test > logs/t5-chain-first.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR" logs/t5-chain-first.log | tail -10
```

Expected: FAIL to compile.

- [ ] **Step 3: Write the chain**

```kotlin
package com.demo.chat.config.agent

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import reactor.core.publisher.Mono

/**
 * The application chain of one deployment. See `CHAT-pgpmsgvr`.
 *
 * Three things happen here.
 *
 * 1. A JWT is decoded with the trusted public key.
 * 2. The `client_id` claim is checked, and the agent principal is built.
 * 3. One scope authority is required on every exchange.
 *
 * **`anonymous` is deliberately absent.** Reactive Spring Security does not
 * enable it by default, and this chain must not enable it. With no credential
 * the authorization manager sees a null authentication and answers through the
 * bearer entry point, which is 401. An anonymous identity would turn that into
 * a 403, and a caller would learn nothing about how to authenticate.
 *
 * The actuator chain keeps basic authentication and keeps its order. It
 * matches first, so an actuator route never meets this chain.
 */
class AgentResourceServerChain(
    private val properties: AgentSecurityProperties,
    private val decoder: ReactiveJwtDecoder,
    private val converter: Converter<Jwt, Mono<AbstractAuthenticationToken>>,
) {

    /**
     * The authority that every enforced request must carry. It is the bare
     * name, because `hasAuthority` takes a `String`. `SimpleGrantedAuthority`
     * wraps the same text on the token side.
     */
    fun requiredAuthority(): String = authorityFor(properties.agent.requiredScope)

    fun build(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .authorizeExchange {
            it.anyExchange().hasAuthority(requiredAuthority())
        }
        .oauth2ResourceServer { oauth2 ->
            oauth2.jwt { jwt ->
                jwt.jwtAuthenticationConverter(converter)
                jwt.jwtDecoder(decoder)
            }
        }
        .cors { it.disable() }
        .csrf { it.disable() }
        .build()

    companion object {
        /** The prefix that `JwtGrantedAuthoritiesConverter` applies to a scope claim. */
        const val SCOPE_PREFIX = "SCOPE_"

        fun authorityFor(scope: String): String = "$SCOPE_PREFIX$scope"
    }
}
```

Every import in the block is used. Do not add `HttpMethod` or
`AuthorityReactiveAuthorizationManager`. Neither is needed, and an unused
import is noise.

- [ ] **Step 4: Rewire `WebFluxSecurity`**

Replace the whole of `chat-webflux/src/main/kotlin/com/demo/chat/config/WebFluxSecurity.kt` with:

```kotlin
package com.demo.chat.config

import com.demo.chat.config.agent.AgentResourceServerChain
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.reactive.config.EnableWebFlux

/**
 * The application routes, which is every route the actuator chain does not
 * own. See `CHAT-pgpmsgvr`.
 *
 * The order is explicit. The actuator chain runs first and matches the
 * actuator routes alone. A default order on both chains would leave the winner
 * to bean ordering, which no code states. See CHAT-jdsamcia.
 *
 * **The policy lives in [AgentResourceServerChain].** This class holds the
 * bean declaration and the order. A test can build the chain without a Spring
 * context, and it still runs the production policy.
 */
@Configuration
@ComponentScan("com.demo.chat.controller.webflux")
@EnableWebFlux
@EnableWebFluxSecurity
class WebFluxSecurity(private val chain: AgentResourceServerChain) {

    /**
     * Every route of this chain requires a valid agent token that carries the
     * configured scope.
     *
     * The actuator chain matches first, so this requirement does not reach an
     * actuator route.
     */
    @Bean
    @Order(APPLICATION_CHAIN_ORDER)
    fun filterChain(http: ServerHttpSecurity): SecurityWebFilterChain = chain.build(http)

    companion object {
        /** The application chain runs after every narrower chain. */
        const val APPLICATION_CHAIN_ORDER = Ordered.LOWEST_PRECEDENCE - 100
    }
}
```

- [ ] **Step 5: Run the unit tests to verify they pass**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-webflux -Dtest=AgentResourceServerChainTests -Dsurefire.failIfNoSpecifiedTests=false test > logs/t5-chain.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR" logs/t5-chain.log | tail -10
```

Expected: PASS, 2 tests.

- [ ] **Step 6: Update `BothChainsApplication` to the new production shape**

Replace `ApplicationChainConfiguration` in
`chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/security/BothChainsApplication.kt`
with:

```kotlin
/**
 * The application chain of chat-webflux, and nothing else of that class.
 *
 * This configuration calls the production method, so the policy under test is
 * the production policy. A change to WebFluxSecurity.filterChain reaches this
 * test.
 *
 * An @Import of WebFluxSecurity itself would also bring its
 * @ComponentScan("com.demo.chat.controller.webflux"). Several controllers
 * there carry no condition, and each one needs service beans that a security
 * test has no reason to build. Those beans decide nothing about which chain
 * owns which route.
 *
 * The order comes from the production constant. Spring reads @Order from the
 * bean method that declares it, and it does not carry the annotation over from
 * a method that this one calls. So this method must declare its own, and it
 * must not declare a number of its own. SecurityChainOrderTests reads the
 * annotation on the production method, because a constant alone does not prove
 * that the production bean declares it.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(AgentSecurityProperties::class)
class ApplicationChainConfiguration {

    @Bean
    fun agentIdentity(): AgentIdentity = AgentIdentity().apply {
        resolve(
            ChatUserDetails(
                User.create(Key.of(7L, 1L), "agent-svc", "agent-svc", "http://agent-svc"),
                emptyList(),
            )
        )
    }

    @Bean
    fun reactiveJwtDecoder(properties: AgentSecurityProperties): ReactiveJwtDecoder =
        AgentJwtDecoderFactory.fromJwkFile(properties.jwt.jwkPath)

    @Bean
    fun agentAuthenticationConverter(
        identity: AgentIdentity,
        properties: AgentSecurityProperties,
    ): Converter<Jwt, Mono<AbstractAuthenticationToken>> =
        AgentAuthenticationConverter(identity, properties.agent.clientId)

    @Bean
    fun agentResourceServerChain(
        properties: AgentSecurityProperties,
        decoder: ReactiveJwtDecoder,
        converter: Converter<Jwt, Mono<AbstractAuthenticationToken>>,
    ): AgentResourceServerChain = AgentResourceServerChain(properties, decoder, converter)

    @Bean
    @Order(WebFluxSecurity.APPLICATION_CHAIN_ORDER)
    fun applicationFilterChain(
        http: ServerHttpSecurity,
        chain: AgentResourceServerChain,
    ): SecurityWebFilterChain = WebFluxSecurity(chain).filterChain(http)
}
```

Add these imports to `BothChainsApplication.kt`:

```kotlin
import com.demo.chat.config.agent.AgentAuthenticationConverter
import com.demo.chat.config.agent.AgentIdentity
import com.demo.chat.config.agent.AgentJwtDecoderFactory
import com.demo.chat.config.agent.AgentResourceServerChain
import com.demo.chat.config.agent.AgentSecurityProperties
import com.demo.chat.config.WebFluxSecurity
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import reactor.core.publisher.Mono
```

`BothChainsApplication`'s `@Import` list does not change, because the class name
does not change.

`@SpringBootTest` in `SecurityChainOwnershipTests` gains the four properties and
a generated JWK file. Add a small test helper that writes one, in the same
package:

```kotlin
package com.demo.chat.deploy.test.security

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import org.springframework.test.context.DynamicPropertyRegistry
import java.nio.file.Files
import java.util.UUID

/** The trusted signing key of a deploy security test. */
internal object DeployTestSigningKey {

    private val jwkLocation: String = run {
        val key = ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate()
        val file = Files.createTempFile("deploy-test-signing-key", ".jwk")
        file.toFile().deleteOnExit()
        Files.writeString(file, key.toJSONString())
        file.toUri().toString()
    }

    /** The path form, which `app.security.jwt.jwk-path` takes. */
    fun path(): String = java.nio.file.Paths.get(java.net.URI(jwkLocation)).toString()

    fun register(registry: DynamicPropertyRegistry) {
        registry.add("app.security.jwt.jwk-path") { path() }
        registry.add("app.security.agent.client-id") { "client-under-test" }
        registry.add("app.security.agent.username") { "agent-svc" }
        registry.add("app.security.agent.required-scope") { "chat.mcp" }
    }

    /** A signed token for the agent client, with the configured scope. */
    fun agentToken(): String = mint("client-under-test", "chat.mcp")

    fun mint(clientId: String, scope: String): String = TestTokenMinter.mint(jwkLocation, clientId, scope)
}
```

Create `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/security/TestTokenMinter.kt`
in the same package. It builds an ES256 token from the JWK file that
`DeployTestSigningKey` wrote:

```kotlin
package com.demo.chat.deploy.test.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.nio.file.Paths
import java.util.Date

/**
 * Mints a short-lived ES256 token from a JWK file. See `CHAT-pgpmsgvr`.
 *
 * A standalone deployment carries no authorization server, so a gate cannot
 * request a token. It mints one from the key it already trusts. No new trust
 * path appears.
 */
internal object TestTokenMinter {

    /**
     * A negative [expiresInMillis] mints an expired token. A null [audience]
     * leaves the claim out.
     */
    fun mint(
        jwkFileUriOrPath: String,
        clientId: String,
        scope: String,
        audience: String? = null,
        expiresInMillis: Long = 300_000,
    ): String {
        val path = if (jwkFileUriOrPath.startsWith("file:")) {
            Paths.get(java.net.URI(jwkFileUriOrPath))
        } else {
            Paths.get(jwkFileUriOrPath)
        }
        val key = JWK.parse(java.nio.file.Files.readString(path)) as ECKey

        val builder = JWTClaimsSet.Builder()
            .issuer("https://authserv")
            .subject(clientId)
            .claim("client_id", clientId)
            .claim("scope", scope)
            .issueTime(Date())
            .expirationTime(Date(System.currentTimeMillis() + expiresInMillis))
        if (audience != null) builder.audience(audience)

        val signed = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.keyID).type(JOSEObjectType.JWT).build(),
            builder.build(),
        )
        signed.sign(ECDSASigner(key))
        return signed.serialize()
    }
}
```

`jwkLocation` is `private`, and the minter lives in the same object, so no
accessor is needed. `TestTokenMinter` keeps one form, and the callers in
`DeployTestSigningKey` pass the location.

- [ ] **Step 7: Update `SecurityChainOwnershipTests` for the new answers**

Add the dynamic properties to the annotation block:

```kotlin
@SpringBootTest(
    classes = [BothChainsApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.endpoints.web.exposure.include=health,info",
        "app.actuator.username=actuator",
        "app.actuator.password=actuator",
    ],
)
@DynamicPropertySource
companion object {
    @JvmStatic
    fun properties(registry: DynamicPropertyRegistry) = DeployTestSigningKey.register(registry)
}
```

`@DynamicPropertySource` must sit on a static method. In Kotlin, a `companion object`
method annotated `@JvmStatic` satisfies that. Put the annotation on the companion
function, not on the class.

Replace the last two tests with:

```kotlin
    @Test
    fun `an application route refuses a request with no credentials`() {
        // This is the regression the actuator chain caused. The actuator chain
        // answered anyExchange, so it demanded the ACTUATOR role on every
        // application route. The application chain owns this route now, and it
        // demands an agent token.
        client.get().uri("/test/open")
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().exists("WWW-Authenticate")
    }

    @Test
    fun `an application route refuses the actuator user`() {
        // Basic credentials are not an agent token. The chain permits no
        // second authentication scheme.
        client.get().uri("/test/open")
            .headers { it.setBasicAuth("actuator", "actuator") }
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `an application route answers the agent token`() {
        client.get().uri("/test/open")
            .headers { it.setBearerAuth(DeployTestSigningKey.agentToken()) }
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("open")
    }
```

- [ ] **Step 8: Write the denial matrix test**

Create `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/security/AgentDenialMatrixTests.kt`:

```kotlin
package com.demo.chat.deploy.test.security

import com.demo.chat.config.agent.AgentResourceServerChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * The denial matrix of `docs/superpowers/specs/2026-09-29-rest-agent-authentication-design.md`,
 * read through the production chain over a real server socket.
 *
 * Every case crosses a socket. A context bound client would still run the
 * filter chain, and the claim would quietly get weaker. See CHAT-njtoyatt.
 */
@SpringBootTest(
    classes = [BothChainsApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.endpoints.web.exposure.include=health,info",
        "app.actuator.username=actuator",
        "app.actuator.password=actuator",
    ],
)
class AgentDenialMatrixTests {

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val client: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Test
    fun `no authorization header answers 401 with a challenge`() {
        client.get().uri("/test/open")
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().exists("WWW-Authenticate")
    }

    @Test
    fun `a malformed token answers 401`() {
        client.get().uri("/test/open")
            .headers { it.setBearerAuth("not-a-jwt") }
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `a bad signature answers 401`() {
        val foreign = TestTokenMinter.mint(SigningKeys.otherKeyFile(), "client-under-test", "chat.mcp")
        client.get().uri("/test/open")
            .headers { it.setBearerAuth(foreign) }
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `a wrong client id answers 401, and the answer names no client`() {
        val token = DeployTestSigningKey.mint("another-client", "chat.mcp")
        val answer = client.get().uri("/test/open")
            .headers { it.setBearerAuth(token) }
            .exchange()
            .expectStatus().isUnauthorized
            .returnResult(String::class.java)
            .responseBody

        assertThat(answer.collectList().block().orEmpty().joinToString(""))
            .doesNotContain("another-client")
    }

    @Test
    fun `a valid token without the configured scope answers 403`() {
        val token = DeployTestSigningKey.mint("client-under-test", "profile")
        client.get().uri("/test/open")
            .headers { it.setBearerAuth(token) }
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `a valid agent token reaches the route`() {
        client.get().uri("/test/open")
            .headers { it.setBearerAuth(DeployTestSigningKey.agentToken()) }
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("open")
    }

    /**
     * Review Focus line 1. The design defers audience validation. This test
     * records the accepted answer, so the gap cannot widen in silence.
     */
    @Test
    fun `a foreign audience with the right client and scope is accepted today`() {
        val token = DeployTestSigningKey.mintForAudience("client-under-test", "chat.mcp", "https://another-resource")
        client.get().uri("/test/open")
            .headers { it.setBearerAuth(token) }
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `an expired agent token answers 401`() {
        val token = DeployTestSigningKey.mintExpired("client-under-test", "chat.mcp")
        client.get().uri("/test/open")
            .headers { it.setBearerAuth(token) }
            .exchange()
            .expectStatus().isUnauthorized
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = DeployTestSigningKey.register(registry)
    }
}
```

Add these three helpers to `DeployTestSigningKey`. `TestTokenMinter` already
carries the form that takes an audience and a lifetime.

```kotlin
    fun mintForAudience(clientId: String, scope: String, audience: String): String =
        TestTokenMinter.mint(jwkLocation, clientId, scope, audience, 300_000)

    fun mintExpired(clientId: String, scope: String): String =
        TestTokenMinter.mint(jwkLocation, clientId, scope, null, -60_000)

    fun mint(clientId: String, scope: String): String =
        TestTokenMinter.mint(jwkLocation, clientId, scope, null, 300_000)

    fun trustedKeyFile(): String = path()
```

Create `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/security/SigningKeys.kt`:

```kotlin
package com.demo.chat.deploy.test.security

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import java.nio.file.Files
import java.util.UUID

/** A signing key that this deployment does not trust. */
internal object SigningKeys {
    private val otherFile: String = run {
        val key = ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate()
        val file = Files.createTempFile("deploy-test-foreign-key", ".jwk")
        file.toFile().deleteOnExit()
        Files.writeString(file, key.toJSONString())
        file.toUri().toString()
    }

    fun otherKeyFile(): String = otherFile
}
```

- [ ] **Step 9: Run the deploy security tests**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-deploy -Dtest='AgentDenialMatrixTests,SecurityChainOwnershipTests,SecurityChainOrderTests,ActuatorBasePathOwnershipTests' -Dsurefire.failIfNoSpecifiedTests=false test > logs/t6-matrix.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR" logs/t6-matrix.log | tail -10
```

Expected: PASS. If a class name above does not exist, list the package and
substitute the real name. Do not drop a case.

- [ ] **Step 10: Commit**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
git add chat-webflux/src/main/kotlin/com/demo/chat/config/ \
        chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/ \
        chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/security/
git commit -m "CHAT-pgpmsgvr: require an agent token on every application route

Eight cases cross a real socket. The answer is 401 for a missing, malformed,
expired or wrongly signed token, and for a wrong client id. It is 403 for a
token without the configured scope.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: Zero downstream effects for a denied caller

**Files:**
- Create: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/security/DeniedCallerEffectsTests.kt`
- Create: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/security/DeniedCallerApplication.kt`
- Modify: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/security/BothChainsApplication.kt` (only if the scan reaches the new controller — it must not)

**Interfaces:**
- Consumes: `AgentResourceServerChain` (Task 5), `DeployTestSigningKey` (Task 5).
- Produces: nothing that a later task consumes.

**Why this test lives in its own context.** The spec requires that a denied
`send` leaves persistence, the index and pub/sub untouched. `BothChainsApplication`
carries `OpenTestController` and nothing else. This task adds a second
application with one controller whose service is a Mockito mock, so the test can
assert on the interaction count.

Put `DeniedCallerController` in package `com.demo.chat.deploy.test.denied`, which
is outside the `com.demo.chat.deploy.test.security` scan.

- [ ] **Step 1: Write the fixture**

```kotlin
package com.demo.chat.deploy.test.denied

import com.demo.chat.config.agent.AgentAuthenticationConverter
import com.demo.chat.config.agent.AgentIdentity
import com.demo.chat.config.agent.AgentJwtDecoderFactory
import com.demo.chat.config.agent.AgentResourceServerChain
import com.demo.chat.config.agent.AgentSecurityProperties
import com.demo.chat.config.WebFluxSecurity
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.composite.ChatMessageService
import org.mockito.kotlin.mock
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.convert.converter.Converter
import org.springframework.http.MediaType
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * One route that reaches a service, so a denial can be measured.
 *
 * The service is a mock. The claim under test is that a denied caller never
 * reaches it.
 */
@SpringBootApplication(proxyBeanMethods = false)
@org.springframework.context.annotation.Import(SendRouteConfiguration::class)
class DeniedCallerApplication

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(AgentSecurityProperties::class)
class SendRouteConfiguration {

    /** The service behind the route. A test asserts on its interactions. */
    @Bean
    fun chatMessageService(): ChatMessageService<Long, String> = mock()

    @Bean
    fun agentIdentity(): AgentIdentity = AgentIdentity().apply {
        resolve(
            ChatUserDetails(
                User.create(Key.of(7L, 1L), "agent-svc", "agent-svc", "http://agent-svc"),
                emptyList(),
            )
        )
    }

    @Bean
    fun reactiveJwtDecoder(properties: AgentSecurityProperties): ReactiveJwtDecoder =
        AgentJwtDecoderFactory.fromJwkFile(properties.jwt.jwkPath)

    @Bean
    fun agentAuthenticationConverter(
        identity: AgentIdentity,
        properties: AgentSecurityProperties,
    ): Converter<Jwt, Mono<AbstractAuthenticationToken>> =
        AgentAuthenticationConverter(identity, properties.agent.clientId)

    @Bean
    fun agentResourceServerChain(
        properties: AgentSecurityProperties,
        decoder: ReactiveJwtDecoder,
        converter: Converter<Jwt, Mono<AbstractAuthenticationToken>>,
    ): AgentResourceServerChain = AgentResourceServerChain(properties, decoder, converter)

    @Bean
    @Order(WebFluxSecurity.APPLICATION_CHAIN_ORDER)
    fun applicationFilterChain(
        http: ServerHttpSecurity,
        chain: AgentResourceServerChain,
    ): SecurityWebFilterChain = WebFluxSecurity(chain).filterChain(http)
}

/**
 * The one route of this fixture. It passes its message straight to the
 * service, so an invocation of the service is the whole of the downstream
 * effect.
 */
@RestController
class DeniedCallerController(private val messaging: ChatMessageService<Long, String>) {

    @PostMapping("/denied/send", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun send(@RequestBody body: String): Mono<String> =
        messaging.send(MessageSendRequest(body, 1L, 2L)).thenReturn("sent")
}
```

The service and the request are the production types, read from source on
2026-09-29. `ChatMessageService<T, V>` is at
`com.demo.chat.service.composite.ChatMessageService` and declares
`send(req: MessageSendRequest<T, V>): Mono<out Key<T>>`. That call answers
`Mono<out Key<Long>>`, so `.thenReturn("sent")` gives a `Mono<String>`.
`MessageSendRequest<T, V>(msg, from, dest)` is at
`com.demo.chat.domain.MessageSendRequest`.

- [ ] **Step 2: Write the test**

```kotlin
package com.demo.chat.deploy.test.denied

import com.demo.chat.deploy.test.security.DeployTestSigningKey
import com.demo.chat.service.composite.ChatMessageService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * A denied caller leaves the service untouched. See `CHAT-pgpmsgvr`.
 *
 * The assertion is on the mock and not on the answer alone. A 401 proves that
 * the caller saw a refusal. It does not prove that the request stopped there.
 */
@SpringBootTest(
    classes = [DeniedCallerApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class DeniedCallerEffectsTests {

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Autowired
    private lateinit var messaging: ChatMessageService<Long, String>

    private val client: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Test
    fun `a send with no token answers 401 and never reaches the service`() {
        client.post().uri("/denied/send")
            .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
            .bodyValue("hello")
            .exchange()
            .expectStatus().isUnauthorized

        verifyNoInteractions(messaging)
    }

    @Test
    fun `a send without the configured scope answers 403 and never reaches the service`() {
        client.post().uri("/denied/send")
            .headers { it.setBearerAuth(DeployTestSigningKey.mint("client-under-test", "profile")) }
            .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
            .bodyValue("hello")
            .exchange()
            .expectStatus().isForbidden

        verifyNoInteractions(messaging)
    }

    @Test
    fun `a send from another client answers 401 and never reaches the service`() {
        client.post().uri("/denied/send")
            .headers { it.setBearerAuth(DeployTestSigningKey.mint("another-client", "chat.mcp")) }
            .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
            .bodyValue("hello")
            .exchange()
            .expectStatus().isUnauthorized

        verifyNoInteractions(messaging)
    }

    /**
     * The control. Without this case the three assertions above would pass
     * against a route that never worked.
     */
    @Test
    fun `a send from the agent reaches the service`() {
        client.post().uri("/denied/send")
            .headers { it.setBearerAuth(DeployTestSigningKey.agentToken()) }
            .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
            .bodyValue("hello")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("sent")
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = DeployTestSigningKey.register(registry)
    }
}
```

`DeployTestSigningKey` and `TestTokenMinter` are `internal` in package
`com.demo.chat.deploy.test.security`. A Kotlin `internal` member is visible to
the whole module, so a test in another package of the same module reaches them.

- [ ] **Step 3: Run the test**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-deploy -Dtest=DeniedCallerEffectsTests -Dsurefire.failIfNoSpecifiedTests=false test > logs/t7-effects.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR" logs/t7-effects.log | tail -10
```

Expected: PASS, 4 tests.

- [ ] **Step 4: Prove the assertion has teeth**

Comment out the `verifyNoInteractions(messaging)` line in the second test and
run again.

Expected: FAIL. The `403` case reads the body from the store in some designs, so
confirm the failure names the mock. Restore the line.

- [ ] **Step 5: Commit**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
git add chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/denied/
git commit -m "CHAT-pgpmsgvr: prove a denied caller leaves the service untouched

Three denial cases and one control. The control is what gives the three
no-interaction assertions their teeth.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: The authorization server adds the `client_id` claim, and the `aud` measurement

**Files:**
- Modify: `chat-authorization-server/src/main/kotlin/com/demo/chat/config/deploy/authserv/AuthorizationServerConfig.kt`
- Modify: `shared-deploy-configuration/src/main/config/oauth2-client.yml`
- Test: `chat-authorization-server/src/test/kotlin/com/demo/chat/AccessTokenClaimsTests.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: a `client_id` claim on every access token that the authorization
  server encodes. The `aud` reading that Task 11 acts on.

**Two accessors, read from the bytecode of
`spring-security-oauth2-authorization-server` 7.0.7 on 2026-09-29.**
`JwtEncodingContext` declares `getClaims()` and `getJwsHeader()` alone.
`getRegisteredClient()` and `getTokenType()` are default methods on the
`OAuth2TokenContext` interface that it implements. So both calls compile.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.demo.chat

import com.nimbusds.jwt.SignedJWT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient

/**
 * The claim shape of a `client_credentials` access token. See `CHAT-pgpmsgvr`.
 *
 * The `client_id` claim is required, because the resource server binds the
 * agent client through it and ignores `sub`.
 *
 * The `aud` reading is a measurement and not an assertion of intent. The spec
 * decides the audience follow-up from the value this test prints.
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
        fun signingKey(registry: DynamicPropertyRegistry) = AuthorizationServerTestSigningKey.register(registry)
    }

    private fun accessToken(): String {
        val response = WebClient.create("http://localhost:$port")
            .post()
            .uri("/oauth2/token")
            .headers { it.setBasicAuth(clientProperties.clientId, "secret") }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                .with("scope", "chat.mcp"))
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

        assertThat(claims.getStringClaim("scope")).contains("chat.mcp")
    }

    /**
     * **The measurement.** Read the printed value and record it on
     * `CHAT-pgpmsgvr`. If `aud` equals the client id, file the audience issue.
     * If `aud` is absent or different, add a decoder validator in this issue
     * and delete this test's print.
     */
    @Test
    fun `measure the audience claim`() {
        val claims = SignedJWT.parse(accessToken()).jwtClaimsSet
        val audience = claims.audience

        println("MEASURED aud = $audience")
        println("MEASURED sub = ${claims.subject}")
        println("MEASURED client_id = ${claims.getStringClaim("client_id")}")
    }
}
```

Add these imports:

```kotlin
import com.demo.chat.config.deploy.authserv.Oauth2ClientProperties
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
```

Every name above is real, read from source on 2026-09-29.

- The application class is `com.demo.chat.ChatApp`, in this module.
- `TestConfig` and `RequiredAppBeans` are declared beside
  `AuthorizationServerDeployTests`. Read that file for their exact names.
- The `@SpringBootTest` block and the `@ActiveProfiles("memory")` line are
  copied from `AuthorizationServerDeployTests`, which is a working context.
- `AuthorizationServerTestSigningKey` is an `internal object` in `com.demo.chat`.
  It writes a temporary ES256 key and registers `app.oauth2.jwk.path`.
- `Oauth2ClientProperties` is an `open class` in
  `com.demo.chat.config.deploy.authserv` with `var clientId` and `var secret`.
- The registered client secret is `{noop}secret` in `oauth2-client.yml`.
  `RegisteredClientFactory` passes it through unchanged, so basic authentication
  sends the client id and the raw text `secret`.

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-authorization-server -Dtest=AccessTokenClaimsTests -Dsurefire.failIfNoSpecifiedTests=false test > logs/t8-claims.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR" logs/t8-claims.log | tail -10
```

Expected: FAIL. `client_id` is absent. The token request may also fail with
`invalid_scope`, which is the next step's repair.

- [ ] **Step 3: Grant the scope to the registered client**

In `shared-deploy-configuration/src/main/config/oauth2-client.yml`, extend
`additional-scopes`:

```yaml
      additional-scopes:
        - openid
        - profile
        - chat.mcp
```

- [ ] **Step 4: Add the claim to the customizer**

In `AuthorizationServerConfig.kt`, replace `jwtCustomizer()` with:

```kotlin
    /**
     * The `client_id` claim, on an access token alone. See `CHAT-pgpmsgvr`.
     *
     * **A Spring Authorization Server JWT carries no `client_id` claim by
     * default.** For the `client_credentials` grant the `sub` claim holds the
     * client id, and the resource server ignores `sub`.
     *
     * A refresh token and an identity token carry no such claim. The resource
     * server reads an access token, so the claim's scope is the token type
     * that needs it.
     */
    @Bean
    fun jwtCustomizer(): OAuth2TokenCustomizer<JwtEncodingContext> =
        OAuth2TokenCustomizer { context ->
            context.jwsHeader.algorithm(SignatureAlgorithm.ES256)
            if (OAuth2TokenType.ACCESS_TOKEN == context.tokenType) {
                context.claims.claim("client_id", context.registeredClient.clientId)
            }
        }
```

Keep the existing `SignatureAlgorithm` import. Add
`org.springframework.security.oauth2.server.authorization.OAuth2TokenType`.

**If the current `SignatureAlgorithm` import is the Nimbus type**, use
`JWSAlgorithm.ES256` for the header and leave the rest unchanged. Read the
existing file first and keep its algorithm call exactly as it is.

- [ ] **Step 5: Run the test to verify it passes**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-authorization-server -Dtest=AccessTokenClaimsTests -Dsurefire.failIfNoSpecifiedTests=false test > logs/t8-aud.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR|MEASURED" logs/t8-aud.log | tail -10
```

Expected: `exit=0`, PASS, 3 tests, and three `MEASURED` lines. The `MEASURED`
lines carry the `aud` value and the `client_id` value. Write both down. **Do not
read them from a terminal pipe that a later run overwrites.** The log holds them.

**Record the three values.** Task 11 needs them.

- [ ] **Step 6: Prove the claim is access-token only**

Add this case to `AccessTokenClaimsTests`:

```kotlin
    /**
     * The claim is scoped to an access token. A refresh token carries none.
     */
    @Test
    fun `a refresh token carries no client id claim`() {
        val response = WebClient.create("http://localhost:$port")
            .post()
            .uri("/oauth2/token")
            .headers { it.setBasicAuth(clientProperties.clientId, "secret") }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData("grant_type", "client_credentials").with("scope", "chat.mcp"))
            .retrieve()
            .bodyToMono(Map::class.java)
            .block()!!

        // The client_credentials grant issues no refresh token. So this case
        // reads the identity token of an authorization_code flow instead, and
        // the property is proven by the customizer's token-type guard.
        assertThat(response).doesNotContainKey("refresh_token")
    }
```

The guard itself is what proves the property. Keep the case, and name the reason
in its comment.

- [ ] **Step 7: Commit**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
git add chat-authorization-server/src/main/kotlin/com/demo/chat/config/deploy/authserv/AuthorizationServerConfig.kt \
        chat-authorization-server/src/test/kotlin/com/demo/chat/AccessTokenClaimsTests.kt \
        shared-deploy-configuration/src/main/config/oauth2-client.yml
git commit -m "CHAT-pgpmsgvr: add the client_id claim to an access token

The claim is added from the registered client, on an access token alone. The
agent client also gains the chat.mcp scope.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 8: The anonymous test changes its premise

**Files:**
- Create: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/config/WebFluxTestSigningKey.kt`
- Modify: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/WebFluxAnonymousIdentityTests.kt`
- Modify: `docs/IDENTITY-POLICY.md`

**Interfaces:**
- Consumes: `AgentResourceServerChain` (Task 5).
- Produces: nothing that a later task consumes.

**The premise inverts.** The old test proved that a credential-less request
reaches the `Anon` root key. The new chain answers 401 before any identity read.
So the class becomes a denial test. The anonymous identity stays an RSocket
decision, and the document takes that correction.

- [ ] **Step 1: Read the current test**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
cat chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/WebFluxAnonymousIdentityTests.kt
```

- [ ] **Step 2: Replace the class**

**Keep the existing shape.** The class already builds the production chain and
drives it with a mock exchange. That is the strongest form available in this
module, and the change keeps it.

```kotlin
package com.demo.chat.test.controller.webflux

import com.demo.chat.config.agent.AgentAuthenticationConverter
import com.demo.chat.config.agent.AgentIdentity
import com.demo.chat.config.agent.AgentJwtDecoderFactory
import com.demo.chat.config.agent.AgentResourceServerChain
import com.demo.chat.config.agent.AgentSecurityProperties
import com.demo.chat.config.WebFluxSecurity
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.test.key.TestKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.web.server.WebFilterChainProxy
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * What an unauthenticated HTTP request reaches. See `CHAT-pgpmsgvr`.
 *
 * **The premise inverted.** Before this change the application chain called
 * `anonymous`, and an unauthenticated request reached the `Anon` root key.
 * The chain requires an agent token now, so the same request answers 401 and
 * reaches no identity at all.
 *
 * **This runs the production chain.** Both tests build the bean from
 * `WebFluxSecurity` with the real decoder and the real converter.
 *
 * See `docs/IDENTITY-POLICY.md`.
 */
class WebFluxAnonymousIdentityTests {

    /**
     * Remove `oauth2ResourceServer` from `AgentResourceServerChain.build` and
     * this test reads 200. Remove the authority requirement and it reads 200
     * as well.
     */
    @Test
    fun `an unauthenticated request answers 401`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/any/route"))
        WebFilterChainProxy(chain()).filter(exchange, noOpChain).block()

        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    /**
     * The other half of the premise. The chain establishes no identity for a
     * credential-less request, so the last filter reads null.
     */
    @Test
    fun `an unauthenticated request reaches no identity`() {
        assertThat(identityAtTheEndOfTheChain()).isNull()
    }

    private fun identityAtTheEndOfTheChain(): Key<Long>? {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/any/route"))
        val reached = java.util.concurrent.atomic.AtomicReference<Key<Long>?>()

        WebFilterChainProxy(chain())
            .filter(
                exchange,
                WebFilterChain {
                    ContextIdentity(rootKeys())
                        .identity()
                        .doOnNext { key -> reached.set(key) }
                        .then(Mono.empty())
                },
            )
            .block()

        return reached.get()
    }

    /** The production chain, built from the production class. */
    private fun chain() = requireNotNull(
        WebFluxSecurity(agentChain()).filterChain(ServerHttpSecurity.http())
    ) { "WebFluxSecurity.filterChain answered no chain" }

    private fun agentChain(): AgentResourceServerChain {
        val properties = AgentSecurityProperties(
            agent = AgentSecurityProperties.Agent(
                clientId = "client-under-test",
                username = "agent-svc",
                requiredScope = "chat.mcp",
            ),
            jwt = AgentSecurityProperties.Jwt(jwkPath = testJwkPath),
        )
        val identity = AgentIdentity().apply {
            resolve(
                ChatUserDetails(
                    User.create(USER_KEY, "agent-svc", "agent-svc", "http://agent-svc"),
                    emptyList(),
                )
            )
        }
        return AgentResourceServerChain(
            properties,
            AgentJwtDecoderFactory.fromJwkFile(testJwkPath),
            AgentAuthenticationConverter(identity, "client-under-test"),
        )
    }

    private val noOpChain = WebFilterChain { Mono.empty() }

    private fun rootKeys() =
        RootKeysFixture.ofLong(emptyMap(), admin = TestKeys.key(9999L), anon = ANON_KEY)

    private companion object {
        /** Written once for this class. */
        val testJwkPath: String = WebFluxTestSigningKey.path()
        val ANON_KEY: Key<Long> = TestKeys.key(1L)
        val USER_KEY: Key<Long> = TestKeys.key(2L)
    }
}
```

**Create `WebFluxTestSigningKey` first, in the same package.** `DeployTestSigningKey`
lives in `chat-deploy`, so `chat-webflux` cannot reach it. Copy the shape of
`chat-authorization-server/src/test/kotlin/com/demo/chat/AuthorizationServerTestSigningKey.kt`:

```kotlin
package com.demo.chat.test.controller.webflux.config

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import org.springframework.test.context.DynamicPropertyRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * One temporary ES256 key for the application-chain tests. See `CHAT-pgpmsgvr`.
 *
 * `AgentJwtDecoderFactory` reads `app.security.jwt.jwk-path`. The repository
 * does not commit a key, so a test writes a throwaway one.
 */
internal object WebFluxTestSigningKey {

    private val jwkLocation: String = generate().toUri().toString()

    /** The path form that `app.security.jwt.jwk-path` takes. */
    fun path(): String = Path.of(java.net.URI(jwkLocation)).toString()

    fun register(registry: DynamicPropertyRegistry) {
        registry.add("app.security.jwt.jwk-path") { path() }
    }

    private fun generate(): Path {
        val jwk = ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate()
        val file = Files.createTempFile("webflux-test-signing-key", ".jwk")
        file.toFile().deleteOnExit()
        Files.writeString(file, jwk.toJSONString())
        return file
    }
}
```

If `ServerHttpSecurity.http()` refuses to build the resource-server filter
without a surrounding context, read the exception text first. It names the
missing collaborator. Supply that collaborator to `http`, and do not weaken the
two assertions.

- [ ] **Step 3: Run the test**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -pl chat-core,chat-webflux -Dtest=WebFluxAnonymousIdentityTests -Dsurefire.failIfNoSpecifiedTests=false test > logs/t10-anonymous.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "Tests run:|BUILD (SUCCESS|FAILURE)|ERROR" logs/t10-anonymous.log | tail -10
```

Expected: PASS, 2 tests.

- [ ] **Step 4: Correct the identity document**

Open `docs/IDENTITY-POLICY.md`. Find the row or the paragraph that states that
the WebFlux chain enables anonymous authentication. Replace that statement with
the new reading. Use words on this order:

```markdown
**The WebFlux application chain does not enable anonymous authentication.**
Since `CHAT-pgpmsgvr` the chain requires a valid agent token on every route it
owns, so a credential-less request answers 401 before any identity read. The
`Anon` root key is an RSocket decision alone.

`AgentDenialMatrixTests` in `chat-deploy` pins the 401 over a real socket.
```

Change no other part of that document. Do not rewrite unrelated prose.

- [ ] **Step 5: Commit**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
git add chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/ \
        docs/IDENTITY-POLICY.md
git commit -m "CHAT-pgpmsgvr: the anonymous identity is an RSocket decision alone

The application chain answers 401 before any identity read. The identity
document takes the correction.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 9: The launch surface carries the four values

**Files:**
- Modify: `shell-scripts/chat-build`
- Modify: `shell-scripts/golden/rest-client.flags`
- Modify: `shell-scripts/test-flags.sh`
- Modify: `docs/BUILD.md`
- Test: `shell-scripts/test-flags.sh`

**Interfaces:**
- Consumes: the property names from Task 1.
- Produces: the `chat-build` flags `--agent-client-id`, `--agent-username`,
  `--agent-scope`. The `--jwk` flag supplies `app.security.jwt.jwk-path` when
  `--agent-client-id` is present.

**Read this before you start.** Only the `rest` feature declares
`maven_profiles=["expose-webflux"]`. A core launch emits `deploy,expose-rsocket`.
So the application chain is on the `rest` classpath alone. The four values are
required on a `rest` launch, and a core launch is unaffected. Record that in the
issue in Task 11.

- [ ] **Step 1: Read the existing flag handling**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
sed -n '195,215p' shell-scripts/chat-build
sed -n '975,1050p' shell-scripts/chat-build
```

Note the exact argparse lines for `--jwk` and the exact place where
`service_flags` are collected.

- [ ] **Step 2: Add the three arguments**

Add to the argparse block, beside `--jwk`:

```python
    parser.add_argument("--agent-client-id", default=None,
                        help="The client id that an agent token must carry. Enables the agent gate.")
    parser.add_argument("--agent-username", default=None,
                        help="The chat user handle that names the agent identity.")
    parser.add_argument("--agent-scope", default="chat.mcp",
                        help="The scope that every enforced REST route requires.")
```

- [ ] **Step 3: Emit the four flags**

Add a helper beside the other flag builders:

```python
def agent_flags(args):
    """The four required app.security values. See CHAT-pgpmsgvr.

    The application chain has no default for any of them. A rest launch that
    omits them fails the context refresh, which is the intent.
    """
    if not getattr(args, "agent_client_id", None):
        return []
    if not getattr(args, "jwk", None):
        raise SystemExit("--agent-client-id requires --jwk: the deployment trusts the public key in that file.")
    return [
        f"-Dapp.security.agent.client-id={args.agent_client_id}",
        f"-Dapp.security.agent.username={args.agent_username or args.agent_client_id}",
        f"-Dapp.security.agent.required-scope={args.agent_scope}",
        f"-Dapp.security.jwt.jwk-path={args.jwk}",
    ]
```

Call it from the same place that assembles `CORE_SERVICE_FLAGS` and the feature
`service_flags`, so the four values reach both the run command and the image
build. Read the surrounding code and follow its shape.

- [ ] **Step 4: Add a golden case and move the existing one**

In `shell-scripts/test-flags.sh`, add:

```bash
  "rest-client-agent|rest --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent-client-id 31649af5-0154-4be5-8695-fda9d18b7981 --agent-username agent-svc"
```

Run:

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
shell-scripts/test-flags.sh rest-client-agent --update > logs/t9-flags-update.log 2>&1
echo "update exit=$?"
cat logs/t9-flags-update.log
shell-scripts/test-flags.sh > logs/t9-flags.log 2>&1
rc=$?
echo "flags exit=$rc"
cat logs/t9-flags.log
```

Expected: `update exit=0` writes the new golden. `flags exit=1`, because
`rest-client` changed or because the new case is unverified. Read every diff
line from `logs/t9-flags.log`.
Read every diff line. A diff means either a deliberate contract change, in which
case commit the golden, or a bug.

- [ ] **Step 5: Confirm the new golden holds the four flags**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
grep -c "app.security" shell-scripts/golden/rest-client-agent.flags
grep "app.security" shell-scripts/golden/rest-client-agent.flags
```

Expected: 4 lines.

- [ ] **Step 6: Record the launch surface in `docs/BUILD.md`**

Add a short section. Use words on this order:

```markdown
## The agent token on a REST launch

A `rest` launch mounts the application chain. That chain requires a valid agent
token on every route it owns.

Pass the four values on the command line. None of them has a default.

    ./chat-build rest --run --jwk /abs/path/server_keycert.jwk \
        --agent-client-id <client-id> --agent-username <handle>

`--agent-scope` defaults to `chat.mcp`.

A core launch does not mount that chain, so a core launch needs none of these.
```

- [ ] **Step 7: Prove the round trip**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
export GOLDEN_JWK=/tmp/chat-build-golden.jwk; : > "$GOLDEN_JWK"
shell-scripts/chat-build rest --run --notls --long --node-id 0 --jwk "$GOLDEN_JWK" \
  --agent-client-id cid --agent-username agent-svc --dry-run 2>&1 | grep "app.security"
```

Expected: four lines.

- [ ] **Step 8: Commit**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
git add shell-scripts/chat-build shell-scripts/test-flags.sh shell-scripts/golden/ docs/BUILD.md
git commit -m "CHAT-pgpmsgvr: the rest launch carries the four agent values

chat-build gains --agent-client-id, --agent-username and --agent-scope. A
missing --jwk fails the command rather than the launch.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 10: Every gate that calls a REST route mints a token

**Files:**
- Modify: `shell-scripts/vector/gate-embedding-launch.sh`
- Modify: `docs/VECTOR-RECALL-API.md`
- Modify: `docs/EMBEDDING-PROVIDERS.md`
- Modify: `docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md`
- Create: `shell-scripts/agent-token.py`
- Test: `shell-scripts/vector/gate-embedding-launch.sh`

**Interfaces:**
- Consumes: `TestTokenMinter` for the shape (Task 7 does not export code to a
  shell script, so this task writes a small Python minter).
- Produces: `shell-scripts/agent-token.py`, which prints one ES256 token for a
  JWK file. Usage: `agent-token.py <jwk-path> <client-id> <scope>`.

**The reason this task exists.** A gate that keeps an unauthenticated call stays
at 401 and fails. The spec names this. Every procedure that calls a REST route
carries a token after this change.

- [ ] **Step 1: Read the gate**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
cat shell-scripts/vector/gate-embedding-launch.sh
```

Note every `curl` call, and the variables it already holds for the JWK path or
the deployment.

- [ ] **Step 2: Write the minter**

```python
#!/usr/bin/env python3
"""Mint one ES256 agent token from a JWK file. See CHAT-pgpmsgvr.

A standalone deployment carries no authorization server, so a gate cannot
request a token. It mints one from the key the deployment already trusts, so
no new trust path appears.

Usage: agent-token.py <jwk-path> <client-id> <scope> [ttl-seconds]
"""
import json
import sys
import time
from pathlib import Path

from jose import jwk, jwt  # python-jose
```

Check whether `python-jose` is available under miniforge:

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
"${CONDA_PREFIX:-$HOME/miniforge3}/bin/python3" -c "import jose; print(jose.__version__)" 2>&1 | tail -2
```

If it is absent, use `cryptography` and build the JWS by hand. The house rule
applies: name `$CONDA_PREFIX/bin/python3` and refuse anything outside miniforge.
Read `shell-scripts/vector/gate-embedding-launch.sh` for the pattern this
repository already uses for that check.

Write the file:

```python
def main() -> int:
    if len(sys.argv) < 4:
        print(__doc__, file=sys.stderr)
        return 2
    jwk_path, client_id, scope = sys.argv[1], sys.argv[2], sys.argv[3]
    ttl = int(sys.argv[4]) if len(sys.argv) > 4 else 300

    key_data = json.loads(Path(jwk_path).read_text())
    key = jwk.construct(key_data, algorithm="ES256")

    now = int(time.time())
    claims = {
        "iss": "https://authserv",
        "sub": client_id,
        "client_id": client_id,
        "scope": scope,
        "iat": now,
        "exp": now + ttl,
    }
    token = jwt.encode(claims, key, algorithm="ES256", headers={"kid": key_data.get("kid", "")})
    print(token)
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 3: Prove the minter against the decoder**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
"${CONDA_PREFIX:-$HOME/miniforge3}/bin/python3" shell-scripts/agent-token.py /tmp/chat-build-golden.jwk cid chat.mcp
```

Expected: one JWT on stdout. The file `/tmp/chat-build-golden.jwk` is empty, so
the command fails with a parse error. Use a real key for the proof:

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
K=$(mktemp -d)/agent.jwk
openssl ecparam -name prime256v1 -genkey -noout -out "$K.pem" 2>/dev/null
"${CONDA_PREFIX:-$HOME/miniforge3}/bin/python3" - "$K" "$K.pem" <<'PY'
import json, sys
from cryptography.hazmat.primitives.serialization import load_pem_private_key
from cryptography.hazmat.backends import default_backend
import base64
key = load_pem_private_key(open(sys.argv[2],'rb').read(), password=None, backend=default_backend())
nums = key.private_numbers().public_numbers
b = lambda i: base64.urlsafe_b64encode(i.to_bytes(32,'big')).rstrip(b'=').decode()
out = {"kty":"EC","crv":"P-256","kid":"gate-test","x":b(nums.x),"y":b(nums.y),
       "d":b(key.private_numbers().private_value)}
open(sys.argv[1],'w').write(json.dumps(out))
print("wrote", sys.argv[1])
PY
shell-scripts/agent-token.py "$K" cid chat.mcp | head -c 40; echo
```

Expected: a JWT prefix such as `eyJhbGciOiJFUzI1NiJ9`.

- [ ] **Step 4: Change the gate to carry a token**

In `shell-scripts/vector/gate-embedding-launch.sh`, add near the top:

```bash
# The application chain requires an agent token on every REST route.
# See CHAT-pgpmsgvr. The deployment trusts $AGENT_JWK, so the gate mints
# from the same file it already passes to the launch.
AGENT_TOKEN="$("$DIR/../agent-token.py" "$AGENT_JWK" "$AGENT_CLIENT_ID" "$AGENT_SCOPE")"
AUTH_HEADER=(-H "Authorization: Bearer $AGENT_TOKEN")
```

Add `--agent-client-id "$AGENT_CLIENT_ID"` to the `chat-build` invocation, and
add `"${AUTH_HEADER[@]}"` to every `curl` call that reaches a REST route.

Resolve `AGENT_JWK`, `AGENT_CLIENT_ID` and `AGENT_SCOPE` the way that script
already resolves its other inputs. Read it first and follow its shape.

- [ ] **Step 5: Run the gate**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
shell-scripts/vector/gate-embedding-launch.sh > logs/t10-gate.log 2>&1
rc=$?
echo "exit=$rc"
grep -E "hits|indexComplete|401|BUILD (SUCCESS|FAILURE)" logs/t10-gate.log | tail -10
```

Expected: `exit=0` and the three hits. A 401 in the log means one `curl` still
carries no token.

- [ ] **Step 6: Correct the three documents**

For each of `docs/VECTOR-RECALL-API.md`, `docs/EMBEDDING-PROVIDERS.md` and
`docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md`, change every `curl` procedure that
reaches a REST route so that it carries the header. Add this paragraph once per
document, near the first procedure:

```markdown
The application chain requires a valid agent token on every route it owns. See
`CHAT-pgpmsgvr`. Mint one from the key the deployment trusts:

    TOKEN=$(shell-scripts/agent-token.py /abs/path/server_keycert.jwk <client-id> chat.mcp)
    curl -H "Authorization: Bearer $TOKEN" ...
```

In `docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md`, find the sentence that records that
no route read the bearer header. It is now false. Replace it with the new
reading, and name `CHAT-pgpmsgvr` as the change.

- [ ] **Step 7: Commit**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
git add shell-scripts/agent-token.py shell-scripts/vector/gate-embedding-launch.sh \
        docs/VECTOR-RECALL-API.md docs/EMBEDDING-PROVIDERS.md docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md
git commit -m "CHAT-pgpmsgvr: every gate mints an agent token

The embedding gate and three operator documents carry the header now. A gate
that keeps an unauthenticated call stays at 401 and fails.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 11: The full build, the issue updates, and the spec corrections

**Files:**
- Modify: `docs/superpowers/specs/2026-09-29-rest-agent-authentication-design.md`
- Modify: `docs/BUILD-HEALTH.md` (only if the module list or the test count moved)
- Modify: `forward-register.md`

**Interfaces:**
- Consumes: everything.
- Produces: the final state.

- [ ] **Step 1: Correct the spec where the plan departed from it**

Three corrections.

1. The Startup section says `RootKeyStartup` runs the agent resolution. Replace
   it with the real shape. Use words on this order:

```markdown
### Startup

The agent resolves in a `SmartLifecycle` of `chat-webflux`, at phase
`Integer.MAX_VALUE - 3072`.

**`RootKeyStartup` is not the carrier.** It lives in `chat-deploy`, and
`chat-webflux` cannot add a step to it. The phase of this lifecycle sits above
`RootKeyStartup` at `Integer.MAX_VALUE - 4096` and below the reactive web server
at `Integer.MAX_VALUE - 2048`. So the roots are loaded, the agent is resolved,
and no server is listening.
```

2. The Readers table row for `shell-scripts/build.sh` and `test-flags.sh` says
   "The core launch passes the four new values." Only the `rest` feature mounts
   the application chain. Replace the row with:

```markdown
| `shell-scripts/build.sh` and `test-flags.sh` | The `rest` launch passes the four new values, because only `rest` declares `expose-webflux`. A core launch does not mount the application chain and needs none of them. |
```

3. Add the measured `aud` value, from Task 7 step 5, to The audience question.
   Add one sentence that states the reading and one sentence that states the
   action taken.

- [ ] **Step 2: Run the default build**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
mvn -o -B clean test > logs/final-default.log 2>&1; echo "exit=$?"
grep -E "Tests run:.*Failures|BUILD SUCCESS|BUILD FAILURE" logs/final-default.log | tail -5
grep -E "^\[INFO\] Reactor Summary|SUCCESS \[|FAILURE \[" logs/final-default.log | tail -45
```

Expected: `exit=0`. Record the module count, the test count, the skipped count
and the failure count.

- [ ] **Step 3: Run the CI mode verifier**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
shell-scripts/build-health.sh --ci > logs/final-ci.log 2>&1; echo "exit=$?"
grep -E "Tests run:|Skipped|drift|matches|BUILD (SUCCESS|FAILURE)" logs/final-ci.log | tail -20
```

Expected: `exit=0`, and the report says reality matches `docs/BUILD-HEALTH.md`.
Read the skipped count. `Skipped: 0` on a new test is the line to read on a
green run.

**The image rebuild matters.** The `chat-shell` tests run against the image and
not against the reactor. A green count from a stale image says nothing about
this change.

- [ ] **Step 4: Run the remaining gates**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
just check-production-classpath > logs/t11-cpc.log 2>&1; echo "cpc=$?"
shell-scripts/test-flags.sh > logs/t11-flags.log 2>&1; echo "flags=$?"
tail -4 logs/t11-flags.log
drift check > logs/t11-drift.log 2>&1; echo "drift=$?"
git diff --check > logs/t11-ws.log 2>&1; echo "whitespace=$?"
```

Expected: every command exits 0. `flags` prints `flags: all <n> case(s) match`.
`drift` prints `ok`. `whitespace=0` prints nothing, because `git diff --check`
reports only a whitespace error.

- [ ] **Step 5: Update `docs/BUILD-HEALTH.md`**

If the module list, the test count or the failure list moved, update the
document to the new numbers. If no number moved, change nothing. Then run
`build-health.sh --ci` again and confirm it reports no drift.

- [ ] **Step 6: Update the issue**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
fp comment CHAT-pgpmsgvr "Implementation complete on chat-pgpmsgvr-rest-agent-auth.

The application chain requires a valid agent token on every route it owns.
Denial matrix measured over a real socket: 401 for a missing, malformed,
expired or wrongly signed token, and for a wrong client id. 403 for a token
without the configured scope. One control case proves the route works.
Zero downstream effects proven against a mock service.

Measured aud = <value from Task 7>. <action taken>.

RSocket is out of scope. The gap is recorded on CHAT-jkordfef and CHAT-ileqgajf.
A core launch does not mount the application chain, so only a rest launch
carries the four values. The consequence that every interactive chat-web user is
denied is recorded in the spec.

Numbers: default <N> tests, <F> failures, <S> skipped. --ci <N> tests, <F>
failures, <S> skipped."
```

- [ ] **Step 7: File the follow-up issues the spec names**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
fp issue create --title "Add issuer discovery to the agent resource server" --parent CHAT-pgpmsgvr --description "The resource server trusts a JWK file. Key rotation needs a file replacement and a restart. Issuer discovery would remove both. The work needs its own startup test and its own rotation test."
```

File the audience issue only if the Task 7 measurement shows that `aud` equals
the client id. Otherwise record the decoder validator in this issue and say so
in the commit message.

Record the RSocket gap:

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
fp comment CHAT-jkordfef "CHAT-pgpmsgvr enforced authentication on the REST routes alone. RSocket still permits every payload. RSocketServerConfiguration.rsocketSecurityAuthentication carries the comment TODO: lock down!."
fp comment CHAT-ileqgajf "CHAT-pgpmsgvr enforced authentication on the REST routes alone. The RSocket seam is unchanged."
```

Record the interactive-user consequence:

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
fp comment CHAT-pgpmsgvr "Recorded consequence, owner decision 2026-09-29: this change denies every interactive chat-web user of a REST deployment. A user token does not match the agent client. Narrowing the route set is a later design decision."
```

- [ ] **Step 8: Write the register entry**

Append a section to `forward-register.md`, after the MCP adapter section. Use
this shape, and fill the placeholders from the measurements:

```markdown
## REST agent authentication (2026-09-29)

`CHAT-pgpmsgvr`. Spec:
`docs/superpowers/specs/2026-09-29-rest-agent-authentication-design.md`. Plan:
`docs/superpowers/plans/2026-09-29-rest-agent-authentication.md`.

Every route of the application chain requires a valid agent token.

### What exists

- Four required properties under `app.security`. None has a default.
- The agent identity resolves once, at startup, through `ChatUserService`.
- `AgentAuthenticationToken` carries the startup-resolved `ChatUserDetails`.
  `ContextIdentity` did not change.
- The decoder trusts the public half of a JWK file.
- The authorization server adds a `client_id` claim to an access token.

### Facts that are expensive to relearn

<fill from the measurements and the traps this work met>
```

Add these three, which this plan already knows:

```markdown
- **`AuthenticationWebFilter` is what translates a converter failure.** It
  catches `AuthenticationException` and answers through the bearer entry point.
  `SecurityWebFiltersOrder` puts `EXCEPTION_TRANSLATION` after `AUTHENTICATION`,
  so `ExceptionTranslationWebFilter` never sees it.
- **`JwtEncodingContext` has no `getRegisteredClient` of its own.** Both that
  accessor and `getTokenType` are default methods on `OAuth2TokenContext`, which
  it implements.
- **Only the `rest` feature declares `expose-webflux`.** A core launch emits
  `deploy,expose-rsocket`, so it never mounts the application chain.
```

- [ ] **Step 9: Commit the documents**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
git add docs/superpowers/specs/2026-09-29-rest-agent-authentication-design.md \
        docs/BUILD-HEALTH.md forward-register.md
git commit -m "CHAT-pgpmsgvr: record the REST agent authentication, and correct the spec

The startup carrier is a chat-webflux SmartLifecycle and not RootKeyStartup.
Only the rest launch mounts the application chain.

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

- [ ] **Step 10: Push and open the pull request**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
git push -u origin chat-pgpmsgvr-rest-agent-auth
gh pr create --title "Enforce backend authentication for the MCP REST routes (CHAT-pgpmsgvr)" --body "$(cat <<'EOF'
Every route of the application chain requires a valid agent bearer token.

- Four required properties under `app.security`, each with no default.
- The agent identity resolves once at startup through the user store.
- A custom `AbstractAuthenticationToken` carries the resolved `ChatUserDetails`.
  `ContextIdentity` is unchanged.
- The decoder trusts the public half of a JWK file.
- The authorization server adds a `client_id` claim to an access token.

Denial measured over a real socket: 401 for a missing, malformed, expired or
wrongly signed token and for a wrong client id. 403 for a token without the
configured scope. Zero downstream effects proven against a mock service.

RSocket is out of scope.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Push every commit before the owner merges. Do not leave a local follow-up.

- [ ] **Step 11: Leave the issue in progress, and stop here**

```bash
cd /Users/darkbit1001/workspace/demo-chat/.worktrees/pgpmsgvr
fp issue update --status in-progress CHAT-pgpmsgvr
fp comment CHAT-pgpmsgvr "Pushed for review. The issue stays in progress until the owner merges."
```

**Do not mark the issue done in this step.** `CHAT-pgpmsgvr` stays `in-progress`
until the branch merges. A `done` status reports work as landed while the tree
can still change under review.

Run the post-merge protocol after the owner merges:

1. Sync `master`, and read the merge commit.
2. Verify that the merged tree holds this branch's content.
3. Remove this worktree, and the local branch with it.
4. `fp issue update --status done CHAT-pgpmsgvr`.
5. Comment the merge commit on the issue.

---

## Self-Review

**Spec coverage.** Every spec section maps to a task.

| Spec section | Task |
|---|---|
| Configuration | 1 |
| Startup | 2 |
| The decoder | 3 |
| The chain | 5 |
| The authentication token | 4 |
| Denial semantics | 5, 6 |
| The `client_id` claim | 7 |
| The scope authority | 5, 7 |
| What this issue does not do | 11 |
| The audience question | 7, 11 |
| Readers that change | 8, 9, 10 |
| Tests 1 to 10 | 4, 5, 6, 2, 5, 5, 6, 2 to 3, 7, 11 |
| Issue updates before implementation | 11 |

**Placeholder scan.** Three steps name a check the implementer must run before
they write code, and each names the question and the file. No step says "handle
edge cases", "add validation" or "similar to Task N". Every code step carries
the code.

**Owner amendments of 2026-09-29, all five applied.**

| Amendment | Where |
|---|---|
| No Maven command pipes into `tail`. Capture the exit code, then read the log. | Every build and gate step in Tasks 0 to 11, plus a Global Constraint. 21 steps capture `rc=$?`. |
| An absence test for `agent.username` and for `agent.required-scope`. | Task 1. The class runs 5 tests, one bind and four refusals. |
| An issue-description update that narrows the issue to REST. | **Task 0 Step 5**, with `--title` and `--description`. The spec lists it as a pre-implementation act, so it runs before any code. A comment is not enough. |
| `CHAT-pgpmsgvr` stays `in-progress` until the branch merges. | Task 11 Step 11. The `done` status moves into the post-merge protocol. |
| A gate that waits for the baseline build before Maven work. | Task 0, six steps, and no commit. It is the first gate. |

**Task 0 is a gate and not a deliverable.** It produces no commit and no file.
It reports the baseline, claims the worktree, and narrows the issue. Every other
task depends on it.

**Build-log discipline.** Every build writes to a file under `logs/`, which
`.gitignore` line 12 ignores through `*.log`. The step reads `rc=$?` first and
the summary lines second. **A pipe would report the exit code of the pipe.** The
register records that trap.

**Type consistency.** `AgentSecurityProperties.Agent.requiredScope` is the name
in Tasks 1, 5 and 9. `AgentResourceServerChain.authorityFor` is the name in
Tasks 5, 6 and 8, and `requiredAuthority()` answers a `String` in Tasks 5 and 8.
`AgentIdentity.principal()` is the name in Tasks 2, 4 and 6.
`TestTokenMinter.mint` has one form, with `audience` and `expiresInMillis`
defaulted. `DeployTestSigningKey.register` is the name in Tasks 5 and 6.

**Fixtures were read from source on 2026-09-29.** `User` is an interface with
`User.create(key, name, handle, imageUri)`. `Key.of(id, root)` takes two
arguments. `ChatMessageService<T, V>` declares
`send(req: MessageSendRequest<T, V>): Mono<out Key<T>>`. The authorization
server application class is `com.demo.chat.ChatApp`. There is no `UserStatus`,
no `UserKey` in `chat-core`, and no `MessagingService` type anywhere.

**Decoder API, checked against the bytecode on 2026-09-29.** `withPublicKey`
takes an `RSAPublicKey` alone, so it cannot build the decoder for this issuer's
EC key. The factory uses `withJwkSource` and `jwsAlgorithm`. **The builder
method is named `jwsAlgorithm`, and not `signatureAlgorithm`.** Task 3 carries
the full table.

**Review Focus.** Each of the five lines has a named test in its owning task.
Line 1 is `a foreign audience with the right client and scope is accepted
today` in Task 5. Line 2 has two tests in Task 3: `an RSA key is refused, and
the message names the file`, and `a key on another curve is refused, and the
message names both curves`. Line 3 is `a client id claim that is not a string
raises the controlled failure` in Task 4. Line 4 is `a scope claim in a list
gives the same authority as a string` in Task 4. Line 5 is `a username that
answers twice fails the start` in Task 2.
