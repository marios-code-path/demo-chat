# The MCP agent identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to
> implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for
> tracking.
>
> **This project forbids subagent-driven development.** `AGENTS.md` states the
> rule twice. Use the Native route. Do not dispatch a subagent per task.

**Goal:** Give the MCP adapter its own chat user, so an adapter credential stops
carrying the administrator rights of the `Admin` account.

**Architecture:** `userinit.yml` declares one more `initialUsers` entry. That
entry names no `ChatIdentity`, so `InitialUsersService` must stop refusing a
third initial user. The entry carries no password, so the service must generate
one and print it. Nothing else moves: no grant row, no `ChatIdentity` value, no
`RootKeys` parameter, no wire shape.

**Tech Stack:** Kotlin 2.4.20, JDK 25, Spring Boot 4.0.8, Spring Security 7,
Maven multi-module reactor, JUnit 5, AssertJ, Mockito, Reactor.

**Spec:** `docs/superpowers/specs/2026-10-01-mcp-agent-identity-design.md`

## Global Constraints

- Branch: `chat-werokcbb-agent-identity`, cut from master `c069c978`.
- **Do not merge without explicit owner approval.**
- Run the **full reactor**. A scoped `-pl` run resolves upstream modules from
  `~/.m2` and reports failures that are not real.
- Run `shell-scripts/build-health.sh` in default mode and in `--ci` mode.
- Read the exit code of the image build before reporting an integration result.
- Run `drift check` for every bound document, and `git diff --check`.
- Merge commits only. Squash and rebase are disabled at the repository.
- Use `fp` for task tracking. Run `fp comment CHAT-werokcbb "<milestone>"` at
  every milestone. Never use a markdown checklist for a task.
- Write every agent-authored sentence in strict-mode Controlled English.
- Keep the credential file and every private key outside the repository.
  `encrypt-keys/` is ignored at `.gitignore:41`, and a private key must not
  reach the working tree at all. Task 5 step 1 generates the signing key in a
  temporary directory.
- `app.security.agent.username` has no default. Every launch must set it.
- Preserve the shipped authorization rows. Ten rows stand, and no row is added.

## Review Focus

Five input classes the spec implies but no existing test exercises. Each line
names the input and the behaviour a reader would expect.

1. **A `userinit.yml` that omits `Admin` or `Anon`.** The start must fail
   loudly, and must not load a partial identity set. Task 1.
2. **A blank `password` on an account that already holds a stored credential.**
   The new value must replace the stored one, and the console line must name the
   account. Task 2.
3. **A third initial user whose name is also a domain name, such as `User`.**
   The user must load as a plain user. `RootKeys.byName("User")` must still
   answer the `User` domain root. Task 1.
4. **An `app.security.agent.username` that matches zero users or two users.**
   The start must fail, and the message must name the handle and the count.
   `AgentIdentityLifecycleTests` covers that rule already. Task 3 pins the other
   half: the shipped file creates exactly one such user.
5. **A `userinit.yml` entry that carries an explicit password.** No generated
   line may reach the console, and the explicit value must be the stored one.
   Task 2.

---

### Task 1: A non-identity initial user loads as a plain user

**Files:**
- Modify: `chat-deploy/src/main/kotlin/com/demo/chat/service/init/InitialUsersService.kt:129-142`
- Create: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/init/InitialUsersFixture.kt`
- Create: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/init/InitialUsersIdentityTests.kt`

**Interfaces:**
- Consumes: nothing from an earlier task.
- Produces:
  - `InitialUsersFixture`, a class in package
    `com.demo.chat.deploy.test.init`. Constructor takes one optional argument,
    `encoder: PasswordEncoder`.
  - `InitialUsersFixture.userService: ChatUserService<Long>`
  - `InitialUsersFixture.authorizationService: AuthorizationService<Long, AuthMetadata<Long>>`
  - `InitialUsersFixture.secretsStore: RecordingSecretsStore`
  - `InitialUsersFixture.service(properties: UserInitializationProperties): InitialUsersService<Long>`
  - `InitialUsersFixture.roots(): RootKeys<Long>`
  - `RecordingSecretsStore`, a class in the same package. It implements
    `SecretsStore<Long>` over a `LinkedHashMap<Key<Long>, String>`.
    `stored(key: Key<Long>): String?` answers the value for one key.

- [ ] **Step 1: Write the failing test**

Create `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/init/InitialUsersFixture.kt`.

```kotlin
package com.demo.chat.deploy.test.init

import com.demo.chat.config.deploy.init.InitalRoles
import com.demo.chat.config.deploy.init.RoleDefinition
import com.demo.chat.config.deploy.init.UserDefinition
import com.demo.chat.config.deploy.init.UserInitializationProperties
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.init.InitialUsersService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.test.TestLongKeyGenerator
import com.demo.chat.test.anyBoolean
import com.demo.chat.test.anyObject
import com.demo.chat.test.key.TestKeys
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.springframework.security.crypto.password.PasswordEncoder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The scaffolding that both initial-user test classes share.
 *
 * The user service and the authorization service are mocks. **The secrets store
 * is real**, so a test reads the credential that the service actually wrote.
 * See `CHAT-werokcbb`.
 */
class InitialUsersFixture(val encoder: PasswordEncoder = Mockito.mock(PasswordEncoder::class.java)) {

    private val keyGenerator = TestLongKeyGenerator()

    val userService: ChatUserService<Long> = Mockito.mock(ChatUserService::class.java)

    @Suppress("UNCHECKED_CAST")
    val authorizationService: AuthorizationService<Long, AuthMetadata<Long>> =
        Mockito.mock(AuthorizationService::class.java) as AuthorizationService<Long, AuthMetadata<Long>>

    val secretsStore = RecordingSecretsStore()

    init {
        // A created user answers a fresh key. A found user is not needed here,
        // so find answers nothing and a failure stays visible.
        BDDMockito.given(userService.addUser(anyObject()))
            .willReturn(Mono.defer { Mono.just(TestKeys.key(keyGenerator.nextId())) })
        BDDMockito.given(userService.findByUsername(anyObject())).willReturn(Flux.empty())
        BDDMockito.given(authorizationService.authorize(anyObject(), anyBoolean())).willReturn(Mono.empty())
    }

    /** The production service under test, wired to every mock of this fixture. */
    fun service(properties: UserInitializationProperties) = InitialUsersService(
        userService, authorizationService, secretsStore, properties, encoder, LongUtil()
    )

    /** A root key set that holds every domain and no identity. */
    fun roots(): RootKeys<Long> = RootKeys<Long>().apply {
        loadDomains(ChatDomain.entries.associateWith { TestKeys.key(keyGenerator.nextId()) })
    }

    companion object {
        /** Properties that name [users], with no role. */
        fun properties(vararg users: Pair<String, UserDefinition>) = UserInitializationProperties(
            "noop",
            InitalRoles(arrayOf("READ"), "*", arrayOf<RoleDefinition>()),
            users.toMap(),
        )

        /** One initial user definition. The password defaults to blank. */
        fun user(name: String, handle: String, password: String = "") =
            UserDefinition(name, handle, "http://$handle.img", password)
    }
}

/**
 * A secrets store over a map.
 *
 * It survives two calls to `initializeUsers`, so a test can read what a second
 * start wrote over what the first start wrote.
 */
class RecordingSecretsStore : SecretsStore<Long> {

    private val rows: MutableMap<Key<Long>, String> = linkedMapOf()

    /** The credential text for one key, or null when no call wrote one. */
    fun stored(key: Key<Long>): String? = rows[key]

    override fun getStoredCredentials(key: Key<Long>): Mono<String> =
        Mono.justOrEmpty(rows[key])

    override fun addCredential(keyCredential: KeyCredential<Long>): Mono<Void> {
        rows[keyCredential.key] = keyCredential.data
        return Mono.empty()
    }

    override fun compareSecret(keyCredential: KeyCredential<Long>): Mono<Boolean> =
        Mono.just(rows[keyCredential.key] == keyCredential.data)
}
```

Create `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/init/InitialUsersIdentityTests.kt`.

```kotlin
package com.demo.chat.deploy.test.init

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Which initial users become identities, and which stay plain users.
 *
 * `Admin` and `Anon` are the two bootstrap root identities. They live in the
 * closed `ChatIdentity` set in `chat-core`. Every other initial user is a plain
 * user. The MCP adapter account takes that route. See `CHAT-werokcbb`.
 */
class InitialUsersIdentityTests {

    /**
     * **A third initial user is a plain user.** It enters the user store and it
     * enters no `RootKeys` identity. So it holds no `Admin` reach.
     */
    @Test
    fun `a third initial user loads as a plain user`() {
        val fixture = InitialUsersFixture()
        val roots = fixture.roots()

        val keys = fixture.service(
            InitialUsersFixture.properties(
                "Anon" to InitialUsersFixture.user("Anon", "Anon"),
                "Admin" to InitialUsersFixture.user("Admin", "Admin"),
                "Agent" to InitialUsersFixture.user("Agent", "Agent"),
            )
        ).initializeUsers(roots)

        assertThat(keys.keys).containsExactlyInAnyOrder("Anon", "Admin", "Agent")
        assertThat(roots.identities().keys.map { it.wireName })
            .describedAs("the loaded identities")
            .containsExactlyInAnyOrder("Admin", "Anon")
        assertThat(keys["Agent"]).isNotEqualTo(roots.admin())
        assertThat(keys["Agent"]).isNotEqualTo(roots.anon())
    }

    /**
     * **A user name that is also a domain name stays a plain user.** No role
     * row can name it, because `RootKeys.byName` reads the name as a domain
     * first. The shipped rows keep their meaning.
     */
    @Test
    fun `an initial user named after a domain loads as a plain user`() {
        val fixture = InitialUsersFixture()
        val roots = fixture.roots()

        fixture.service(
            InitialUsersFixture.properties(
                "Anon" to InitialUsersFixture.user("Anon", "Anon"),
                "Admin" to InitialUsersFixture.user("Admin", "Admin"),
                "User" to InitialUsersFixture.user("User", "User"),
            )
        ).initializeUsers(roots)

        assertThat(roots.identities()).hasSize(2)
        assertThat(roots.byName("User"))
            .describedAs("the name User still names the User domain root")
            .isEqualTo(roots.of(com.demo.chat.domain.knownkey.ChatDomain.USER))
    }

    /** A missing `Admin` key fails the start. The message names the identity. */
    @Test
    fun `a missing Admin identity fails the start`() {
        val fixture = InitialUsersFixture()

        assertThatThrownBy {
            fixture.service(
                InitialUsersFixture.properties(
                    "Anon" to InitialUsersFixture.user("Anon", "Anon"),
                    "Agent" to InitialUsersFixture.user("Agent", "Agent"),
                )
            ).initializeUsers(fixture.roots())
        }.hasMessageContaining("do not name the Admin identity")
    }

    /** A missing `Anon` key fails the start. The message names the identity. */
    @Test
    fun `a missing Anon identity fails the start`() {
        val fixture = InitialUsersFixture()

        assertThatThrownBy {
            fixture.service(
                InitialUsersFixture.properties(
                    "Admin" to InitialUsersFixture.user("Admin", "Admin"),
                    "Agent" to InitialUsersFixture.user("Agent", "Agent"),
                )
            ).initializeUsers(fixture.roots())
        }.hasMessageContaining("do not name the Anon identity")
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
mvn -o -B -pl chat-core,chat-deploy -am -Dtest=InitialUsersIdentityTests \
  -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 | tee /tmp/t1-fail.log | tail -40
```

Expected: `a third initial user loads as a plain user` and
`an initial user named after a domain loads as a plain user` FAIL with
`An initial user names an unknown identity: [Agent]`. The two missing-identity
tests PASS already.

- [ ] **Step 3: Write the minimal implementation**

In `chat-deploy/src/main/kotlin/com/demo/chat/service/init/InitialUsersService.kt`,
replace the `loadIdentities` method, including its KDoc.

```kotlin
    /**
     * This method loads the two identities from the initial users.
     *
     * The `Admin` and `Anon` names must each be present. Any other initial user
     * is a plain user. It takes no identity and no grant, so it holds no
     * administrator reach. The MCP adapter account takes that route. See
     * `CHAT-werokcbb`.
     */
    private fun loadIdentities(rootKeys: RootKeys<T>, identityKeys: Map<String, Key<T>>) {
        val admin = identityKeys[ChatIdentity.ADMIN.wireName]
            ?: throw ChatException("The initial users do not name the ${ChatIdentity.ADMIN.wireName} identity.")
        val anon = identityKeys[ChatIdentity.ANON.wireName]
            ?: throw ChatException("The initial users do not name the ${ChatIdentity.ANON.wireName} identity.")
        rootKeys.loadIdentities(admin, anon)
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn -o -B -pl chat-core,chat-deploy -am -Dtest='InitialUsersIdentityTests,LongMockInitializationTests' \
  -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 | tail -25
```

Expected: PASS. `LongMockInitializationTests` must stay green, because it names
`Admin` and `Anon` and its password encoder is a mock.

- [ ] **Step 5: Commit**

```bash
git add chat-deploy/src/main/kotlin/com/demo/chat/service/init/InitialUsersService.kt \
        chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/init/
git commit -m "Load a non-identity initial user as a plain user (CHAT-werokcbb)"
```

---

### Task 2: A blank password generates a credential, and the console carries it

**Files:**
- Modify: `chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/init/UserInitializationProperties.kt:25-30`
- Modify: `chat-deploy/src/main/kotlin/com/demo/chat/service/init/InitialUsersService.kt:64-71`
- Create: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/init/InitialUsersCredentialTests.kt`

**Interfaces:**
- Consumes: `InitialUsersFixture` and `RecordingSecretsStore` from Task 1.
- Produces:
  - `UserDefinition.password` takes a default of `""`.
  - `InitialUsersService` writes one console line per generated password. The
    line reads `Generated password for account '<handle>': <value>`.
  - The generated value is Base64 URL text with no padding, from 24 random
    bytes. So it is 32 characters and it carries no space, quote or equals sign.

- [ ] **Step 1: Write the failing test**

Create `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/init/InitialUsersCredentialTests.kt`.

```kotlin
package com.demo.chat.deploy.test.init

import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.test.TestLongKeyGenerator
import com.demo.chat.test.key.TestKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * What a blank password does, and what the console shows.
 *
 * **A blank password generates a new credential at every start.** The service
 * writes the generated value to the console one time per account. The owner
 * accepted that an operator who blanks the `Admin` password prints a live
 * administrator credential. See `CHAT-werokcbb`.
 *
 * The encoder is a real `BCryptPasswordEncoder`, so a test proves that the
 * printed text is the text behind the stored hash.
 *
 * **`@ResourceLock` guards `System.out`.** This class replaces the process wide
 * stream, so it must not run beside another class that does. The lock takes
 * effect when parallel execution is on, and it states the requirement when it
 * is off. See `CHAT-werokcbb`.
 */
@ResourceLock("system.out")
class InitialUsersCredentialTests {

    private val console = ByteArrayOutputStream()
    private lateinit var original: PrintStream

    @BeforeEach
    fun captureConsole() {
        original = System.out
        System.setOut(PrintStream(console, true))
    }

    @AfterEach
    fun restoreConsole() {
        System.setOut(original)
    }

    /**
     * The value of the **newest** `Generated password for account '<name>':
     * <value>` line per account. `associate` keeps the last value for a
     * repeated name, so a second start overwrites the first reading.
     */
    private fun generated(): Map<String, String> = console.toString().lines()
        .filter { it.startsWith(GENERATED) }
        .associate { line ->
            val name = line.substringAfter("account '").substringBefore("'")
            name to line.substringAfterLast(": ")
        }

    /**
     * **The printed value is the stored value.** A run that printed a throwaway
     * string would satisfy a weaker test. The hash comparison is the control.
     */
    @Test
    fun `a blank password generates one and stores the printed value`() {
        val fixture = InitialUsersFixture(BCryptPasswordEncoder())
        val roots = fixture.roots()

        fixture.service(
            InitialUsersFixture.properties(
                "Anon" to InitialUsersFixture.user("Anon", "Anon", "_"),
                "Admin" to InitialUsersFixture.user("Admin", "Admin"),
            )
        ).initializeUsers(roots)

        val printed = generated()
        assertThat(printed.keys).containsExactly("Admin")

        val stored = fixture.secretsStore.stored(roots.admin())!!
        assertThat(BCryptPasswordEncoder().matches(printed.getValue("Admin"), stored))
            .describedAs("the printed password matches the stored hash")
            .isTrue()
    }

    /**
     * **A second start replaces the stored credential, and the second printed
     * value is the second stored value.** A test that only compared the two
     * hashes would pass if the service printed a value it never stored.
     */
    @Test
    fun `a second start replaces the stored credential`() {
        val fixture = InitialUsersFixture(BCryptPasswordEncoder())
        val roots = fixture.roots()
        val properties = InitialUsersFixture.properties(
            "Anon" to InitialUsersFixture.user("Anon", "Anon", "_"),
            "Admin" to InitialUsersFixture.user("Admin", "Admin"),
        )

        fixture.service(properties).initializeUsers(roots)
        val first = fixture.secretsStore.stored(roots.admin())!!
        fixture.service(properties).initializeUsers(roots)
        val second = fixture.secretsStore.stored(roots.admin())!!

        assertThat(second).isNotEqualTo(first)
        assertThat(BCryptPasswordEncoder().matches(generated().getValue("Admin"), second))
            .describedAs("the second printed password matches the second stored hash")
            .isTrue()
        assertThat(BCryptPasswordEncoder().matches(generated().getValue("Admin"), first))
            .describedAs("the second printed password does not match the first hash")
            .isFalse()
    }

    /** An explicit password prints no generated line, and it is the stored one. */
    @Test
    fun `an explicit password prints no generated line`() {
        val fixture = InitialUsersFixture(BCryptPasswordEncoder())
        val roots = fixture.roots()

        fixture.service(
            InitialUsersFixture.properties(
                "Anon" to InitialUsersFixture.user("Anon", "Anon", "_"),
                "Admin" to InitialUsersFixture.user("Admin", "Admin", "changeme"),
            )
        ).initializeUsers(roots)

        assertThat(generated()).isEmpty()
        assertThat(BCryptPasswordEncoder().matches("changeme", fixture.secretsStore.stored(roots.admin())!!))
            .describedAs("the explicit password is the stored one")
            .isTrue()
    }

    private companion object {
        const val GENERATED = "Generated password for account '"
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
mvn -o -B -pl chat-core,chat-deploy -am -Dtest=InitialUsersCredentialTests \
  -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 | tee /tmp/t2-fail.log | tail -40
```

Expected: `a blank password generates one and stores the printed value` FAILS
with `Expecting actual not to be empty`, because nothing prints a generated
line. The other two PASS already. The `@BeforeEach` capture proves the console
is readable before the implementation exists.

- [ ] **Step 3: Write the minimal implementation**

In `chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/init/UserInitializationProperties.kt`,
change `UserDefinition`.

```kotlin
/**
 * One initial user.
 *
 * [password] defaults to blank. A blank password generates a new credential at
 * every start, and the service prints it. See `CHAT-werokcbb`.
 */
data class UserDefinition @ConstructorBinding constructor(
    val name: String,
    val handle: String,
    val imageUri: String,
    val password: String = ""
)
```

In `chat-deploy/src/main/kotlin/com/demo/chat/service/init/InitialUsersService.kt`,
add two imports.

```kotlin
import java.security.SecureRandom
import java.util.Base64
```

Add the constant beside `ADMIN_WILDCARD`.

```kotlin
/**
 * The random bytes behind a generated password. Twenty four bytes give thirty
 * two Base64 characters. See `CHAT-werokcbb`.
 */
private const val PASSWORD_BYTES = 24
```

Add the field to the class body.

```kotlin
    private val secureRandom: SecureRandom = SecureRandom()
```

Replace the credential block at lines 64 to 71.

```kotlin
            identityKeys[identity] = thisUserKey

            val secret = credentialSecret(thisUser)
            val thisCredential = KeyCredential(thisUserKey, "${passwordDecoder.encode(secret)}")

            secretsStore
                .addCredential(thisCredential)
                .block()
```

Add the two methods after `initializeUsers`.

```kotlin
    /**
     * This method answers the credential secret for one initial user.
     *
     * A blank password generates one, and the method writes it to the console.
     * The account handle leads the line, so an operator reads the live
     * credential of a generated account. **The value is written at every
     * start**, and `addCredential` overwrites the stored credential. So only
     * the newest output holds the live password. See `CHAT-werokcbb`.
     */
    private fun credentialSecret(user: UserDefinition): String {
        if (user.password.isNotBlank()) return user.password
        val generated = generatePassword()
        println("Generated password for account '${user.handle}': $generated")
        return generated
    }

    /**
     * This method answers a random password. The value carries no padding, so
     * the console line holds no `=` character.
     */
    private fun generatePassword(): String {
        val bytes = ByteArray(PASSWORD_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn -o -B -pl chat-core,chat-deploy -am -Dtest='InitialUsersCredentialTests,InitialUsersIdentityTests,LongMockInitializationTests' \
  -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 | tail -25
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/init/UserInitializationProperties.kt \
        chat-deploy/src/main/kotlin/com/demo/chat/service/init/InitialUsersService.kt \
        chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/init/InitialUsersCredentialTests.kt
git commit -m "Generate a credential for a blank initial password (CHAT-werokcbb)"
```

---

### Task 3: The shipped file declares the agent user, and a deployment creates it

**Files:**
- Modify: `shared-deploy-configuration/src/main/config/userinit.yml:29-39`
- Modify: `chat-deploy/src/test/kotlin/com/demo/chat/test/deploy/init/UserInitConfigBindingTests.kt:30-42`
- Create: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/AgentUserWiringTests.kt`

**Interfaces:**
- Consumes: the plain-user rule from Task 1, and the blank-password rule from
  Task 2.
- Produces: the `Agent` initial user. Handle `Agent`. Name `MCP ADAPTER`.
  Image `chatimg://agent.png`. No `password` key.

- [ ] **Step 1: Write the failing test**

Add one test to `chat-deploy/src/test/kotlin/com/demo/chat/test/deploy/init/UserInitConfigBindingTests.kt`.

```kotlin
    /**
     * **The shipped file declares the MCP adapter account.** The handle is the
     * default that `docs/MCP-CREDENTIAL-ISSUANCE.md` names for
     * `--agent-username`. The entry carries no password, so the start generates
     * one. See `CHAT-werokcbb`.
     */
    @Test
    fun `the shipped file declares an agent account with no password`() {
        val bound = binder().bind("app.init", UserInitializationProperties::class.java).get()

        val agent = bound.initialUsers["Agent"]

        assertThat(agent).describedAs("the Agent initial user").isNotNull
        assertThat(agent!!.handle).isEqualTo("Agent")
        assertThat(agent.password)
            .describedAs("the agent password, which the start generates")
            .isEmpty()
        assertThat(agent.imageUri).isEqualTo("chatimg://agent.png")
    }
```

Create `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/AgentUserWiringTests.kt`.

```kotlin
package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.time.Duration

/**
 * The MCP adapter account, as a deployment creates it from the shipped file.
 *
 * **The agent is a plain user.** It holds no `ChatIdentity`, so it takes no
 * `Admin` reach. `InitialUsersService` writes one `*` row per domain root for
 * the `Admin` key, and the agent key is not that key.
 *
 * **The handle is the match key.** `AgentIdentityLifecycle` resolves
 * `app.security.agent.username` through `ChatUserService.findByUsername`, and it
 * requires exactly one answer. The deployment docs name `Agent`, which is the
 * handle this test reads.
 *
 * The memory deployment claims no node id. See docs/NODEID-CLAIM.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-agent-user", "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=memory", "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth",
        "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.service.security.userdetails", "app.users.create=true"
    ]
)
class AgentUserWiringTests {

    @Autowired
    lateinit var users: ChatUserService<Long>

    @Autowired
    lateinit var stores: PersistenceServiceBeans<Long, String>

    @Autowired
    lateinit var rootKeys: RootKeys<Long>

    private val timeout = Duration.ofSeconds(10)

    /** The agent handle answers exactly one user. That is the resolution rule. */
    @Test
    fun `the agent handle answers exactly one user`() {
        val matches = users.findByUsername(ByStringRequest("Agent")).collectList().block(timeout)!!

        assertThat(matches).describedAs("the users named Agent").hasSize(1)
    }

    /**
     * **The agent key differs from both identities.** A key that equalled the
     * `Admin` key would carry every right of the administrator.
     */
    @Test
    fun `the agent key differs from the Admin and Anon keys`() {
        val agent = agentKey()

        assertThat(agent).isNotEqualTo(rootKeys.admin())
        assertThat(agent).isNotEqualTo(rootKeys.anon())
        assertThat(rootKeys.identities()).describedAs("the loaded identities").hasSize(2)
    }

    /**
     * **No grant row names the agent as its principal.** The agent holds the
     * floor that every identity holds, and nothing more.
     */
    @Test
    fun `no grant row names the agent as its principal`() {
        val principals = stores.authMetaPersistence().all().collectList().block(timeout)!!
            .map { it.principal }
            .toSet()

        assertThat(principals).describedAs("the grant principals").doesNotContain(agentKey())
        assertThat(principals).describedAs("the grant principals").containsExactlyInAnyOrder(
            rootKeys.admin(), rootKeys.anon(), rootKeys.of(ChatDomain.USER)
        )
    }

    /**
     * The agent is a plain user, so no `ChatIdentity` names it. A name that
     * resolved here would let a role row address it.
     */
    @Test
    fun `the agent name is not an identity`() {
        assertThat(rootKeys.byName("Agent")).describedAs("the Agent name in the root key set").isNull()
    }

    private fun agentKey() =
        users.findByUsername(ByStringRequest("Agent")).blockFirst(timeout)!!.key
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
mvn -o -B -pl chat-core,chat-deploy,chat-deploy-memory -am \
  -Dtest='UserInitConfigBindingTests,AgentUserWiringTests' \
  -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 | tee /tmp/t3-fail.log | tail -40
```

Expected: FAIL. `the shipped file declares an agent account with no password`
fails with `Expecting actual not to be null`. Every `AgentUserWiringTests` test
fails with `the users named Agent` answering zero users.

- [ ] **Step 3: Write the minimal implementation**

Add the entry to `shared-deploy-configuration/src/main/config/userinit.yml`,
under `initialUsers`, after the `Admin` entry.

```yaml
      Agent:
        handle: Agent
        name: "MCP ADAPTER"
        imageUri: chatimg://agent.png
```

**The entry carries no `password` key.** `UserDefinition.password` defaults to
blank, so the start generates a credential and prints it.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn -o -B -pl chat-core,chat-deploy,chat-deploy-memory -am \
  -Dtest='UserInitConfigBindingTests,AgentUserWiringTests' \
  -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 | tail -25
```

Expected: PASS, and the deployment log carries one
`Generated password for account 'Agent':` line.

- [ ] **Step 5: Commit**

```bash
git add shared-deploy-configuration/src/main/config/userinit.yml \
        chat-deploy/src/test/kotlin/com/demo/chat/test/deploy/init/UserInitConfigBindingTests.kt \
        chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/AgentUserWiringTests.kt
git commit -m "Declare the MCP agent account in userinit.yml (CHAT-werokcbb)"
```

---

### Task 4: The agent's reach is narrower than the administrator's

**Files:**
- Modify: `chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt`

**Interfaces:**
- Consumes: the agent account from Task 3, as an ordinary user key.
- Produces:
  - `deployedGrants(): List<AuthMetadata<Long>>`. The ten shipped rows, plus one
    `*` row per domain root of this test for `ADMIN_KEY`. That is the set that
    `InitialUsersService` writes.
  - `agentContext(): SecurityContext`. An authenticated caller whose principal
    carries `AGENT_KEY`.
  - `matrixFor(context, grants)` overload. The existing one-argument form keeps
    its meaning and calls the new form with `shippedGrants()`.

- [ ] **Step 1: Write the failing test**

In `chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt`,
add these tests.

```kotlin
    /**
     * **The agent holds no administrator reach.** The `Admin` identity holds a
     * `*` row on every domain root, and `InitialUsersService` writes those rows.
     * The agent is a plain user, so it reaches none of them.
     *
     * **The two matrices differ at exactly two operations.** `addUser` and
     * `deleteRoom` allow for the administrator and deny for the agent. Every
     * other operation reads the same, because the shipped rows name the `User`
     * root and the `Anon` key, and those reach every caller.
     */
    @Test
    fun `the agent holds no administrator reach`() {
        val grants = deployedGrants()
        val agent = matrixFor(agentContext(), grants)
        val admin = matrixFor(adminContext(), grants)

        assertThat(admin.filter { it.value }.keys - agent.filter { it.value }.keys)
            .describedAs("the operations that the administrator holds and the agent does not")
            .containsExactlyInAnyOrder("addUser User NEW", "deleteRoom MessageTopic REM")

        assertThat(agent - admin.keys).describedAs("operations that only the agent holds").isEmpty()

        assertThat(agent).describedAs("the agent matrix").isEqualTo(
            mapOf(
                "addRoom MessageTopic NEW" to true,
                "send room SEND" to false,
                "whoami User FIND" to true,
                "messageById GET" to true,
                "listRooms MessageTopic GET_ALL" to true,
                "addUser User NEW" to false,
                "deleteRoom MessageTopic REM" to false
            )
        )
    }

    /**
     * **The Admin rows alone carry the difference.** Remove them and the two
     * matrices are equal, so no shipped row names the agent.
     */
    @Test
    fun `without the Admin rows the two matrices are equal`() {
        assertThat(matrixFor(agentContext(), shippedGrants()))
            .describedAs("the agent matrix under the shipped rows alone")
            .isEqualTo(matrixFor(adminContext(), shippedGrants()))
    }
```

Change the two helper functions at lines 598 to 608.

```kotlin
    private fun matrixFor(context: SecurityContext?): Map<String, Boolean> =
        matrixFor(context, shippedGrants())

    private fun matrixFor(context: SecurityContext?, grants: List<AuthMetadata<Long>>): Map<String, Boolean> =
        operations().associate { (operation, call) -> operation to allowed(call, context, grants) }
```

Change `allowed` to take the grant set.

```kotlin
    private fun allowed(
        call: (SpringSecurityAccessBrokerService<Long>) -> Mono<Boolean>,
        context: SecurityContext?,
        grants: List<AuthMetadata<Long>>
    ): Boolean {
        val service = SpringSecurityAccessBrokerService(broker(grants), rootKeys(), registry())
        var answer = call(service)
        if (context != null) {
            answer = answer.contextWrite(
                ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context))
            )
        }
        return answer.block() ?: false
    }
```

Add the deployed grant helper beside `shippedGrants`.

```kotlin
    /**
     * The rows that a deployment writes. That is the shipped set plus one `*`
     * row per domain root for the `Admin` identity, which
     * `InitialUsersService` generates. See `CHAT-znprrzhn`.
     */
    private fun deployedGrants(): List<AuthMetadata<Long>> =
        shippedGrants() + rootKeys().domains().values.map { grant(ADMIN_KEY, it, "*") }
```

Add the agent context beside `adminContext`.

```kotlin
    /** A context whose caller is the MCP adapter account. It is a plain user. */
    private fun agentContext() = SecurityContextImpl(
        UsernamePasswordAuthenticationToken(agentDetails(), "secret", listOf())
    )

    private fun agentDetails() =
        ChatUserDetails(User.create(AGENT_KEY, "g", "Agent", "http://g"), listOf("ROLE_AGENT"))
```

Add `AGENT_KEY` to the companion object and to `registry()`'s key list.

```kotlin
        /** The MCP adapter account. It is an object of the `User` domain. */
        val AGENT_KEY: Key<Long> = Key.of(9L, 3L)
```

```kotlin
    private fun registry() = TestVerifiers.holding(
        rootKeys(),
        listOf(ANON_KEY, ADMIN_KEY, USER_ROOT, MESSAGE_ROOT, TOPIC_ROOT, CALLER_KEY, ROOM_KEY, MESSAGE_KEY, AGENT_KEY),
    )
```

- [ ] **Step 2: Run the tests to verify they compile**

```bash
mvn -o -B -pl chat-core,chat-security -am -Dtest=AnonymousAuthorizationMatrixTests \
  -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 | tee /tmp/t4-first.log | tail -40
```

Expected: PASS. **No production file changes in this task**, so the new tests
document behaviour that already holds. Step 3 is what proves they bite.

- [ ] **Step 3: Confirm the mutation**

Temporarily change `deployedGrants()` to return `shippedGrants()` alone.

```bash
mvn -o -B -pl chat-core,chat-security -am -Dtest=AnonymousAuthorizationMatrixTests \
  -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 | tee /tmp/t4-mutation.log | tail -20
```

Expected: `the agent holds no administrator reach` FAILS, because the two
matrices are equal. Restore the helper and confirm the failure is gone. **Read
the compile errors before any mutation result**, because a run that did not
compile proves nothing.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn -o -B -pl chat-core,chat-security -am -Dtest=AnonymousAuthorizationMatrixTests \
  -Dsurefire.failIfNoSpecifiedTests=false test 2>&1 | tail -20
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt
git commit -m "Pin the agent reach against the administrator reach (CHAT-werokcbb)"
```

---

### Task 5: The packaged acceptance run with the agent credential

**Files:**
- Modify: `docs/MCP-CREDENTIAL-ISSUANCE.md`
- Modify: `docs/BUILD.md`
- Modify: `docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md`

**Interfaces:**
- Consumes: the `Agent` account from Task 3.
- Produces: one measured acceptance result, with the four `app.security` values
  in place and `--app.security.agent.username=Agent`.

**This task spends real wall time and opens three processes.** Do it once, and
stop both servers at the end.

- [ ] **Step 1: Build the signing key and the executable jar**

**The key material must stay outside the repository.** `gen-dckeys.sh` writes to
`<dir>/../encrypt-keys`, where `<dir>` is the script directory. So run it from a
copy of `shell-scripts/` in a temporary directory, and the key lands in that
temporary directory. That leaves no window in which a private key sits inside
the working tree.

```bash
KEYS_DIR="$(mktemp -d /tmp/dchat-agent-keys.XXXXXX)"
cp -R shell-scripts "$KEYS_DIR/shell-scripts"
echo "$KEYS_DIR" > /tmp/t5-keys-dir.txt
"$KEYS_DIR/shell-scripts/gen-dckeys.sh" changeme > /tmp/t5-keys.log 2>&1; echo "keys exit=$?"
ls -l "$KEYS_DIR/encrypt-keys/server_keycert.jwk"
git status --short
```

Expected: `keys exit=0`, the JWK is present, and `git status --short` reports no
`encrypt-keys` path. The script runs under `set -x` and does not stop on an
error, so read the file list rather than the exit code alone.

```bash
mvn -o -B -pl chat-deploy-memory -am -Pexpose-webflux,deploy -DskipTests package \
  > /tmp/t5-package.log 2>&1; echo "package exit=$?"
ls -l chat-deploy-memory/target/chat-deploy-memory-0.0.1-exec.jar
```

Expected: exit 0. The jar is about 180 MiB. **Use `-DskipTests`, not
`-Dmaven.test.skip=true`**, because the skip-all flag also skips test jar
creation and a later image build then fails.

- [ ] **Step 2: Start the authorization server**

```bash
KEYS_DIR=$(cat /tmp/t5-keys-dir.txt)
cat > /tmp/t5-stop.sh <<'EOF'
#!/bin/bash
for f in /tmp/t5-authserv.pid /tmp/t5-deploy.pid; do
  if [ -f "$f" ]; then
    pid=$(cat "$f")
    kill "$pid" 2>/dev/null && echo "stopped $pid from $f"
    rm -f "$f"
  fi
done
EOF
chmod +x /tmp/t5-stop.sh

./shell-scripts/chat-build authserv --run --notls --node-id 8 \
  --jwk "$KEYS_DIR/encrypt-keys/server_keycert.jwk" --profile memory > /tmp/t5-authserv.log 2>&1 &
echo $! > /tmp/t5-authserv.pid
timeout 180 bash -c 'until grep -q "Started ChatApp" /tmp/t5-authserv.log; do sleep 2; done'; echo "ready exit=$?"
cat /tmp/t5-authserv.pid
```

Expected: the log prints `Started ChatApp`, and the PID file holds one number.
The token endpoint answers 404 before that line. **If a later step aborts, run
`bash /tmp/t5-stop.sh`.**

- [ ] **Step 3: Request the token and write the credential file**

```bash
mkdir -p "$HOME/.chat-agent-acceptance"
curl -sS -u '31649af5-0154-4be5-8695-fda9d18b7981:secret' \
  -d 'grant_type=client_credentials' -d 'scope=chat.mcp' \
  http://127.0.0.1:9000/oauth2/token \
  | jq -r '.access_token' > "$HOME/.chat-agent-acceptance/credential.txt"
chmod 600 "$HOME/.chat-agent-acceptance/credential.txt"
wc -c "$HOME/.chat-agent-acceptance/credential.txt"
```

Expected: a token of a few hundred bytes. The file lives outside the repository.
`jq` reads the field, so the run needs no Python interpreter.

- [ ] **Step 4: Start the deployment with the agent username**

```bash
KEYS_DIR=$(cat /tmp/t5-keys-dir.txt)
java --enable-native-access=ALL-UNNAMED \
  -jar chat-deploy-memory/target/chat-deploy-memory-0.0.1-exec.jar \
  --app.nodeid=1 --app.key.type=long --app.server.proto=rest \
  --server.port=6892 --management.server.port=6893 \
  --app.service.core.key=memory --app.service.core.persistence=memory \
  --app.service.core.index=lucene --app.service.core.pubsub=memory \
  --app.service.core.secrets=memory \
  --app.service.composite=true --app.service.composite.auth=true \
  --app.service.security.userdetails=true --app.users.create=true \
  --spring.config.additional-location=classpath:/config/userinit.yml \
  --app.controller.persistence=true --app.controller.topic=true \
  --app.controller.user=true --app.controller.message=true \
  --app.controller.key=true --app.controller.index=true \
  --app.security.agent.client-id=31649af5-0154-4be5-8695-fda9d18b7981 \
  --app.security.agent.username=Agent \
  --app.security.agent.required-scope=chat.mcp \
  --app.security.jwt.jwk-path="$KEYS_DIR/encrypt-keys/server_keycert.jwk" \
  > /tmp/t5-deploy.log 2>&1 &
echo $! > /tmp/t5-deploy.pid
timeout 180 bash -c 'until grep -q "Started ChatApp" /tmp/t5-deploy.log; do sleep 2; done'; echo "ready exit=$?"
grep "Generated password for account 'Agent'" /tmp/t5-deploy.log
grep -c "The agent username" /tmp/t5-deploy.log
```

Expected: `Started ChatApp`, one generated-password line for `Agent`, and a
count of 0 for `The agent username`. **A wrong handle fails the start here**,
which is the agent-resolution proof.

- [ ] **Step 5: Create a topic and run the harness**

```bash
TOKEN=$(cat "$HOME/.chat-agent-acceptance/credential.txt")
curl -sS -H "Authorization: Bearer $TOKEN" -X PUT \
  http://127.0.0.1:6892/persist/topic/add \
  -H 'Content-Type: application/json' -d '{"type":"ByNameRequest","name":"mcpagent"}'
```

Record the `key.id` from the answer as `<allowed id>`. Use `1` as the unserved
id. The name is one token, because a hyphen splits the Lucene field.

Build the adapter classpath first.

```bash
mvn -o -B -pl chat-mcp -am dependency:build-classpath \
  -Dmdep.outputFile=/tmp/t5-deps.txt -Dmdep.includeScope=runtime > /tmp/t5-cp.log 2>&1
echo "classpath exit=$?"
echo "chat-mcp/target/classes:$(cat /tmp/t5-deps.txt)" > /tmp/t5-classpath.txt
```

```bash
cat > /tmp/t5-adapter.properties <<EOF
backendBaseUrl=http://127.0.0.1:6892
credentialFile=$HOME/.chat-agent-acceptance/credential.txt
keyType=long
topicIds=<allowed id>,1
EOF
cd chat-mcp/src/test/client
node harness.mjs --read-id <allowed id> --refused-id 1 -- \
  java -cp "$(cat /tmp/t5-classpath.txt)" com.demo.chat.mcp.McpAdapterMainKt \
  --config /tmp/t5-adapter.properties 2>&1 | tee /tmp/t5-harness.log
```

Expected: harness exit 0, `connectError` null, `AUTHENTICATION_REQUIRED` absent
from every call, and the allowed id answered with status 200.

- [ ] **Step 6: Run the control**

```bash
cp "$HOME/.chat-agent-acceptance/credential.txt" /tmp/t5-real-token.txt
printf 'not-a-token' > "$HOME/.chat-agent-acceptance/credential.txt"
```

Repeat the harness command. Expected: every call answers
`AUTHENTICATION_REQUIRED` with `status=401`.

```bash
cp /tmp/t5-real-token.txt "$HOME/.chat-agent-acceptance/credential.txt"
```

- [ ] **Step 7: Record the reading, and stop both servers**

Update the three documents.

- `docs/MCP-CREDENTIAL-ISSUANCE.md`: change `--agent-username Admin` to
  `Agent`. Change the appendix flag to `--app.security.agent.username=Agent`.
  Replace limit 1 with the new state, and keep the sentence that the agent
  account holds the floor and no administrator reach.
- `docs/BUILD.md`: change the agent username in the launch rows.
- `docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md`: add a section for the 2026-10-01
  run, with the measured table from steps 4, 5 and 6.

Stop both processes through their stored identifiers. **A job number such as
`%1` is not stable across steps**, because each step runs in its own shell.

```bash
trap 'bash /tmp/t5-stop.sh' EXIT
bash /tmp/t5-stop.sh
sleep 3
lsof -nP -iTCP:6892 -iTCP:6893 -iTCP:9000 -sTCP:LISTEN || echo "no listener remains"
rm -rf "$(cat /tmp/t5-keys-dir.txt)" /tmp/t5-keys-dir.txt
```

Expected: `stopped <pid> from /tmp/t5-authserv.pid` and
`stopped <pid> from /tmp/t5-deploy.pid`, then `no listener remains`. The
temporary key directory is removed with the private key inside it.

- [ ] **Step 8: Commit**

```bash
git add docs/MCP-CREDENTIAL-ISSUANCE.md docs/BUILD.md docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md
git commit -m "Point the adapter credential procedure at the agent account (CHAT-werokcbb)"
```

---

### Task 6: The authorization document, the register, and the gates

**Files:**
- Modify: `docs/ANONYMOUS-AUTHORIZATION.md`
- Modify: `forward-register.md`

**Interfaces:**
- Consumes: every task above.
- Produces: the closing record.

- [ ] **Step 1: Record the agent identity and risk 4**

In `docs/ANONYMOUS-AUTHORIZATION.md`, add a section that states three facts.

1. The `Agent` account is a plain user. It holds no `ChatIdentity`.
2. Its reach equals the floor that every identity holds. It holds no row of its
   own, and the `Admin` rows do not reach it.
3. **The adapter's read rests on `{user: User, target: MessageTopic, role: GET}`,
   not on an agent row.** A later change to that row would remove the read, and
   no agent-scoped row would restore it.

- [ ] **Step 2: Record the change in the register**

Add one section to `forward-register.md`, in the same shape as the sections
beside it. Name the issue, the branch, the merge commit once it exists, the
three changed source files, and the measured result. **Do not move the
`Checkout` row.** That row records the last substantive merge before a refresh.

- [ ] **Step 3: Run the default gate**

```bash
shell-scripts/build-health.sh > /tmp/t6-default.log 2>&1; echo "exit=$?"
tail -20 /tmp/t6-default.log
```

Expected: exit 0, and the report says that reality matches
`docs/BUILD-HEALTH.md`. If the test count moved, update the document in the same
commit.

- [ ] **Step 4: Run the CI gate**

```bash
export DOCKER_CONFIG=$(mktemp -d)
shell-scripts/build-health.sh --ci > /tmp/t6-ci.log 2>&1; echo "exit=$?"
tail -20 /tmp/t6-ci.log
```

Expected: exit 0. **Read the image build exit code before you report this
result.** A stale image makes the container tests pass against the wrong code.
An empty `DOCKER_CONFIG` avoids the `'username' must not be null` failure.

- [ ] **Step 5: Run the document gates**

```bash
drift check; echo "drift exit=$?"
git diff --check; echo "diff exit=$?"
git status --short
```

Expected: `drift` reports `ok`, `git diff --check` exits 0, and no untracked file
remains except `werokcbb-next-agent.md`.

- [ ] **Step 6: Comment the issue and commit**

```bash
fp comment CHAT-werokcbb "Acceptance run with --app.security.agent.username=Agent measured. Default and --ci gates pass. Ready for owner review."
git add docs/ANONYMOUS-AUTHORIZATION.md forward-register.md
git add docs/BUILD-HEALTH.md   # only when the test counts moved
git commit -m "Record the MCP agent identity (CHAT-werokcbb)"
```

- [ ] **Step 7: Stop for owner review**

Push the branch and open a pull request. **Do not merge.** Report three things
to the owner.

1. The identity shape: one declared initial user, a plain user, no `ChatIdentity`.
2. The grants it holds: the floor alone, and no row of its own.
3. The measured refusal: `addUser User NEW` and `deleteRoom MessageTopic REM`
   deny for the agent and allow for the administrator.
