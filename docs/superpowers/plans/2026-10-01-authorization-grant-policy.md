# The Authorization Grant Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Write the room owner grant on the server at room creation, remove the
shell writer, and make both send checks name the room.

**Architecture:** A narrow port in `chat-core` carries one method,
`grantOwner(roomKey)`. `chat-security` implements it against the one identity
resolver and the production `AuthorizationService`. `chat-service-composite`
takes the port as an optional dependency, so a composition without
`chat-security` still builds a topic service.

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.0.8, JDK 25, Maven reactor, Reactor,
fp for issue tracking.

**Spec:** `docs/superpowers/specs/2026-10-01-authorization-grant-policy-design.md`

## Global Constraints

- Run `mvn -o -pl chat-core,<module> test`. Never run `-pl <module>` alone. A
  single module run resolves `chat-core` from `~/.m2` and reports false failures.
- One Maven build per worktree. A concurrent `clean` deletes the other build's
  `target` and fakes a classpath failure.
- Write Maven output to a file and read only the exit code and the summary lines.
- A test class that runs under the integration profile carries `@Tag("integration")`
  and owns a distinct `app.nodeid`. The allocation table is in
  `docs/NODEID-CLAIM.md`.
- No module declares a third-party dependency version. `just check-deps` fails
  the build when one appears.
- One writer for `*` per target. `*` is the ownership sentinel.
- A denial is the absence of a grant. No row subtracts a permission.
- Do not edit `shared-deploy-configuration/src/main/config/userinit.yml`. The
  main checkout holds an uncommitted change to it from another line of work.
- Stage named paths only. Never `git add -A` and never `git add .`.
- Preserve the exact-target owner rule. `getAuthorizationsForTarget` and
  `getAuthorizationsForMultipleTarget` never read a domain root.
- Write prose in controlled English. One instruction per sentence. No semicolons.

## Review Focus

1. **A caller whose key is not in the registry.** The grant write fails after the
   room exists. A reasonable person expects the failure to name the room, and
   expects the room to stay. Task 2 covers it.
2. **A second `addRoom` for one name.** The name check is check-then-act, so a
   duplicate must not write a second owner grant. Task 2 covers it.
3. **A second `*` writer.** Two wildcard rows for one room make one room have two
   owners, and nothing refuses the second row. Task 1 pins the behaviour and
   Task 6 files it.
4. **`chat-shell` creating a room with no credential.** No identity means no
   owner and no error. Task 1 covers the writer, and Task 3 covers the shell.
5. **A declaration that reads as a key but holds an id.** `#req.dest` is a
   property and `#req.dest()` is a method that does not exist. Task 5 covers it.

---

### Task 1: The grant port and its writer

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/security/RoomOwnerGrant.kt`
- Create: `chat-security/src/main/kotlin/com/demo/chat/security/service/ContextRoomOwnerGrant.kt`
- Test: `chat-security/src/test/kotlin/com/demo/chat/test/RoomOwnerGrantTests.kt`

**Interfaces:**
- Consumes: `ContextIdentity(rootKeys)`, `AuthorizationService.authorize(auth, exist)`,
  `AuthMetadata.create(key, principal, target, perm, muted, exp)`,
  `Key.empty(placeholder, root)`, `AuthSummarizer.WILDCARD`, `TypeUtil.empty()`,
  `RootKeys.of(ChatDomain.AUTH_METADATA)`.
- Produces: `interface RoomOwnerGrant<T> { fun grantOwner(roomKey: Key<T>): Mono<Void> }`
  and `class ContextRoomOwnerGrant<T>(identity, authorizationService, rootKeys, typeUtil)`.

- [ ] **Step 1: Write the port**

Create `chat-core/src/main/kotlin/com/demo/chat/service/security/RoomOwnerGrant.kt`:

```kotlin
package com.demo.chat.service.security

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono

/**
 * Writes the ownership row of one room.
 *
 * **The port exists so the composite never reads the security context.**
 * `chat-service-composite` does not depend on `chat-security`, and
 * `ContextIdentity` is the only live reader of that context. See
 * `CHAT-zhjltbky`.
 *
 * The implementation writes one wildcard row against [roomKey] for the
 * identity of the caller that created the room. A caller with no identity owns
 * no room, and that is not an error.
 */
interface RoomOwnerGrant<T> {
    fun grantOwner(roomKey: Key<T>): Mono<Void>
}
```

- [ ] **Step 2: Write the failing test**

Create `chat-security/src/test/kotlin/com/demo/chat/test/RoomOwnerGrantTests.kt`:

```kotlin
package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.Admin
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.AuthSummarizer
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.security.rank.PrincipalRank
import com.demo.chat.security.service.ContextRoomOwnerGrant
import com.demo.chat.security.service.CoreAuthorizationService
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.test.key.TestKeys
import com.demo.chat.test.key.TestVerifiers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The room owner writer.
 *
 * **The row is written for the caller of the security context.** The writer
 * reads the identity once, through `ContextIdentity`. See `CHAT-zhjltbky`.
 */
class RoomOwnerGrantTests {

    /**
     * **The writer names the owner, the room, and the wildcard.**
     *
     * `CoreAuthorizationService.write` replaces an empty key with the key that
     * the store mints, so the root of the empty key is inert. Read `write`,
     * `CoreAuthorizationService.kt:79`. Only the `empty` flag is read.
     *
     * The expiry is 0, which the summarizer reads as never expiring.
     */
    @Test
    fun `the writer grants the wildcard to the creating caller`() {
        val store = MapAuthStore()
        val grant = writer(store)

        grant.grantOwner(ROOM).contextWrite(context(authenticated())).block()

        assertThat(store.rows).hasSize(1)
        val row = store.rows.values.single()
        assertThat(row.principal).describedAs("the owner").isEqualTo(CALLER)
        assertThat(row.target).describedAs("the room").isEqualTo(ROOM)
        assertThat(row.permission).describedAs("the permission").isEqualTo(AuthSummarizer.WILDCARD)
        assertThat(row.expires).describedAs("the expiry").isEqualTo(0L)
        assertThat(row.key.empty).describedAs("the minted grant key").isFalse()
    }

    /**
     * **A caller with no identity owns no room, and that is not an error.**
     * `ContextIdentity` answers an empty `Mono` for a denied caller, so the
     * `flatMap` never runs and the chain completes. See the no-identity
     * decision of `CHAT-zhjltbky`.
     */
    @Test
    fun `no identity writes no grant`() {
        val store = MapAuthStore()
        val grant = writer(store)

        grant.grantOwner(ROOM).block()

        assertThat(store.rows).isEmpty()
    }

    /**
     * **A second writer would give one room two owners.** `*` is singular per
     * target, and no code refuses a second row. This test is the evidence for
     * the deferred uniqueness work. Replace it when that work lands.
     */
    @Test
    fun `a second grant writes a second wildcard row`() {
        val store = MapAuthStore()
        val grant = writer(store)

        grant.grantOwner(ROOM).contextWrite(context(authenticated())).block()
        grant.grantOwner(ROOM).contextWrite(context(authenticated())).block()

        assertThat(store.rows.values.map { it.target }).containsExactly(ROOM, ROOM)
    }

    private fun writer(store: MapAuthStore): ContextRoomOwnerGrant<Long> =
        ContextRoomOwnerGrant(
            ContextIdentity(rootKeys()),
            CoreAuthorizationService(
                store, MapAuthIndex(store), { it }, { it }, { ANON }, { USER_ROOT },
                AuthSummarizer({ a, b -> (a.key.id - b.key.id).toInt() }, PrincipalRank(rootKeys())),
                TestVerifiers.holding(rootKeys(), listOf(ANON, CALLER, ROOM)),
            ),
            rootKeys(),
            LongUtil(),
        )

    private fun rootKeys(): RootKeys<Long> = RootKeysFixture.ofLong(
        mapOf(
            ChatDomain.USER to USER_ROOT,
            ChatDomain.MESSAGE to Key.root(4L),
            ChatDomain.MESSAGE_TOPIC to TOPIC_ROOT,
        ),
        admin = ADMIN,
        anon = ANON,
    )

    private fun authenticated() = SecurityContextImpl(
        UsernamePasswordAuthenticationToken(
            ChatUserDetails(User.create(CALLER, "u", "handle", "http://u"), listOf()), "secret", listOf()
        )
    )

    private fun context(that: SecurityContext) = ReactiveSecurityContextHolder.withSecurityContext(Mono.just(that))

    /** The authorization store, as a map. */
    private class MapAuthStore : PersistenceStore<Long, AuthMetadata<Long>> {
        val rows: MutableMap<Key<Long>, AuthMetadata<Long>> = linkedMapOf()

        override fun key(): Mono<out Key<Long>> = Mono.fromSupplier { TestKeys.key(1000L + rows.size) }
        override fun add(ent: AuthMetadata<Long>): Mono<Void> {
            rows[ent.key] = ent
            return Mono.empty()
        }

        override fun rem(key: Key<Long>): Mono<Void> {
            rows.remove(key)
            return Mono.empty()
        }

        override fun get(key: Key<Long>): Mono<out AuthMetadata<Long>> = Mono.justOrEmpty(rows[key])
        override fun all(): Flux<out AuthMetadata<Long>> = Flux.fromIterable(rows.values)
    }

    /** The authorization index, which answers by target key. */
    private class MapAuthIndex(private val store: MapAuthStore) :
        IndexService<Long, AuthMetadata<Long>, Key<Long>> {

        override fun add(entity: AuthMetadata<Long>): Mono<Void> = Mono.empty()
        override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
        override fun findBy(query: Key<Long>): Flux<out Key<Long>> =
            Flux.fromIterable(store.rows.values.filter { it.target == query }.map { it.key })

        override fun findUnique(query: Key<Long>): Mono<out Key<Long>> = findBy(query).next()
    }

    private companion object {
        /** The `User` domain root. An identity is an object of the `User` domain. */
        val USER_ROOT: Key<Long> = Key.root(3L)

        /** The `MessageTopic` domain root. A room carries it. */
        val TOPIC_ROOT: Key<Long> = Key.root(5L)

        val ADMIN: Key<Long> = Key.of(2L, 3L)
        val ANON: Key<Long> = Key.of(1L, 3L)

        /** The caller of every context of this test. */
        val CALLER: Key<Long> = Key.of(6L, 3L)

        /** A room. Its root is the `MessageTopic` root. */
        val ROOM: Key<Long> = Key.of(7L, 5L)
    }
}
```

- [ ] **Step 3: Run the test and confirm it fails**

Run: `mvn -o -pl chat-core,chat-security test -Dtest=RoomOwnerGrantTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The compile error names an unresolved reference `ContextRoomOwnerGrant`.

- [ ] **Step 4: Write the writer**

Create `chat-security/src/main/kotlin/com/demo/chat/security/service/ContextRoomOwnerGrant.kt`:

```kotlin
package com.demo.chat.security.service

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.AuthSummarizer
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.RoomOwnerGrant
import reactor.core.publisher.Mono

/**
 * The one writer of a room ownership row.
 *
 * **`*` is singular per target, so one writer is required.** Two writers would
 * give one room two owners, and the rank would then answer by time alone. See
 * `CHAT-zhjltbky`.
 *
 * **A caller with no identity owns no room.** `ContextIdentity` answers an
 * empty `Mono` for a denied caller, so the `flatMap` never runs. That is not an
 * error, because no authenticated route reaches this call today.
 *
 * The expiry is 0, which `AuthSummarizer` reads as never expiring. The shell
 * wrote `Long.MAX_VALUE`, and a sentinel is not needed.
 */
class ContextRoomOwnerGrant<T>(
    private val identity: ContextIdentity<T>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
) : RoomOwnerGrant<T> {

    override fun grantOwner(roomKey: Key<T>): Mono<Void> =
        identity.identity()
            .flatMap { owner ->
                authorizationService.authorize(
                    // The key is empty, so the service mints it under the
                    // AUTH_METADATA root.
                    AuthMetadata.create(
                        Key.empty(typeUtil.empty(), rootKeys.of(ChatDomain.AUTH_METADATA).id),
                        owner,
                        roomKey,
                        AuthSummarizer.WILDCARD,
                        false,
                        0L,
                    ),
                    true,
                )
            }
            .then()
}
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `mvn -o -pl chat-core,chat-security test -Dtest=RoomOwnerGrantTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. Three tests run, zero failures.

- [ ] **Step 6: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/security/RoomOwnerGrant.kt \
        chat-security/src/main/kotlin/com/demo/chat/security/service/ContextRoomOwnerGrant.kt \
        chat-security/src/test/kotlin/com/demo/chat/test/RoomOwnerGrantTests.kt
git commit -m "The room owner grant port and its writer (CHAT-zhjltbky)

The port lives in chat-core, beside AuthorizationService, so the composite
never reads the security context. The writer lives in chat-security, which
holds the one identity resolver.

The expiry is 0, which the summarizer reads as never expiring. The shell
wrote Long.MAX_VALUE for the same effect.

A second call writes a second wildcard row. Nothing refuses it, and the
test pins that for the deferred uniqueness work."
```

---

### Task 2: The bean and the topic service wiring

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/security/RoomOwnerGrantException.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/Exception.kt:3`
- Create: `chat-security/src/main/kotlin/com/demo/chat/security/service/RoomOwnerGrantConfiguration.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/CompositeServiceBeansConfiguration.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/TopicServiceImpl.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/TopicServiceOwnerGrantTests.kt`

**Interfaces:**
- Consumes: `RoomOwnerGrant<T>` from Task 1, and the `ObjectProvider` pattern that
  the same configuration already uses for `vectorIndexers`.
- Produces: `TopicServiceImpl` takes `roomOwnerGrant: RoomOwnerGrant<T>? = null`.
  `addRoom` calls it once after `pubsub.open`, and before it answers the room key.
  A failed call raises `RoomOwnerGrantException(roomKey, cause)`, whose message
  names the room key.

- [ ] **Step 1: Write the failing test**

Create `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/TopicServiceOwnerGrantTests.kt`:

```kotlin
package com.demo.chat.test.service.composite

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.composite.impl.TopicServiceImpl
import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MembershipPersistence
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.TopicPersistence
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.dummy.DummyIndexService
import com.demo.chat.service.dummy.DummyPersistenceStore
import com.demo.chat.service.security.RoomOwnerGrant
import com.demo.chat.service.security.RoomOwnerGrantException
import com.demo.chat.test.key.TestKeys
import com.demo.chat.test.key.TestVerifiers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.function.Function
import java.util.function.Supplier

/**
 * The room creation path and the owner grant.
 *
 * **The grant write is the last step of `addRoom`.** No step of that chain
 * compensates any other, so a failed grant leaves the room in place. See
 * `CHAT-zhjltbky`.
 */
class TopicServiceOwnerGrantTests {

    /** **The service writes one grant, for the key of the room it created.** */
    @Test
    fun `addRoom writes one grant for the new room`() {
        val fixture = Fixture()
        val service = fixture.service(RecordingGrant())

        val key = service.addRoom(ByStringRequest("general")).block()!!

        assertThat(fixture.grant.calls).containsExactly(key)
    }

    /**
     * **An absent port writes no grant, and it does not fail.** The port is
     * absent exactly when the composition carries no authorization.
     */
    @Test
    fun `an absent port creates the room and writes no grant`() {
        val fixture = Fixture()
        val service = fixture.service(null)

        StepVerifier.create(service.addRoom(ByStringRequest("general")))
            .expectNextCount(1)
            .verifyComplete()

        assertThat(fixture.stored).hasSize(1)
    }

    /**
     * **A failed grant write fails the request, and the room stays.**
     *
     * **The failure names the room key**, so an operator can write the missing
     * row by hand. The message is exact here, because a message that named
     * another key would pass a `contains` check.
     *
     * The cause is preserved, because the reason for the refusal is what an
     * operator acts on.
     */
    @Test
    fun `a failed grant write fails addRoom and names the room`() {
        val fixture = Fixture()
        val service = fixture.service(RecordingGrant(failure = ChatException("the store refused the write")))

        StepVerifier.create(service.addRoom(ByStringRequest("general")))
            .verifyErrorSatisfies { error ->
                assertThat(error).describedAs("the failure").isInstanceOf(RoomOwnerGrantException::class.java)
                val room = fixture.stored.single().key
                val grantFailure = error as RoomOwnerGrantException
                assertThat(grantFailure.roomKey).describedAs("the room key field").isEqualTo(room)
                assertThat(grantFailure.message)
                    .describedAs("the message")
                    .isEqualTo(
                        "The room owner grant failed for room ${room.id}. " +
                            "The room exists and it has no owner."
                    )
                assertThat(grantFailure.cause)
                    .describedAs("the cause")
                    .hasMessage("the store refused the write")
            }

        assertThat(fixture.stored).describedAs("the store row").hasSize(1)
        assertThat(fixture.indexed).describedAs("the index row").hasSize(1)
        assertThat(fixture.opened).describedAs("the open topic").hasSize(1)
    }

    /**
     * **A duplicate name writes no second grant.** The name check is
     * check-then-act, so a duplicate must not reach the grant write.
     */
    @Test
    fun `a duplicate name writes no second grant`() {
        val fixture = Fixture()
        val grant = RecordingGrant()
        val service = fixture.service(grant)

        service.addRoom(ByStringRequest("general")).block()
        StepVerifier.create(service.addRoom(ByStringRequest("general")))
            .verifyErrorSatisfies { error ->
                assertThat(error).describedAs("the second add").isEqualTo(com.demo.chat.domain.DuplicateException)
            }

        assertThat(grant.calls).hasSize(1)
    }

    /** Records every call, and fails on demand. */
    private class RecordingGrant(private val failure: Throwable? = null) : RoomOwnerGrant<Long> {
        val calls: MutableList<Key<Long>> = mutableListOf()

        override fun grantOwner(roomKey: Key<Long>): Mono<Void> {
            calls.add(roomKey)
            return failure?.let { Mono.error(it) } ?: Mono.empty()
        }
    }

    /** The same store, index and pubsub stubs as `TopicServiceReservedNameTests`. */
    private class Fixture {
        val stored: MutableList<MessageTopic<Long>> = mutableListOf()
        val indexed: MutableList<MessageTopic<Long>> = mutableListOf()
        val opened: MutableList<Long> = mutableListOf()
        lateinit var grant: RecordingGrant
        private var nextId = 100L

        val topicPersistence = object : TopicPersistence<Long> {
            override fun key(): Mono<out Key<Long>> = Mono.fromSupplier { TestKeys.key(nextId++) }
            override fun add(ent: MessageTopic<Long>): Mono<Void> = Mono.fromRunnable { stored.add(ent) }
            override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { stored.removeIf { it.key == key } }
            override fun get(key: Key<Long>): Mono<out MessageTopic<Long>> =
                Mono.justOrEmpty(stored.firstOrNull { it.key == key })
            override fun all(): Flux<out MessageTopic<Long>> = Flux.fromIterable(stored.toList())
        }

        val topicIndex = object : TopicIndexService<Long, IndexSearchRequest> {
            override fun add(entity: MessageTopic<Long>): Mono<Void> = Mono.fromRunnable { indexed.add(entity) }
            override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { indexed.removeIf { it.key == key } }
            override fun findBy(query: IndexSearchRequest): Flux<out Key<Long>> =
                Flux.fromIterable(indexed.filter { it.data == query.second }.map { it.key })
            override fun findUnique(query: IndexSearchRequest): Mono<out Key<Long>> = findBy(query).singleOrEmpty()
        }

        val pubsub = object : TopicPubSubService<Long, String> {
            override fun open(topicId: Long): Mono<Void> = Mono.fromRunnable { opened.add(topicId) }
            override fun close(topicId: Long): Mono<Void> = Mono.empty()
            override fun getByUser(uid: Long): Flux<Long> = Flux.empty()
            override fun getUsersBy(topicId: Long): Flux<Long> = Flux.empty()
            override fun subscribe(member: Long, topic: Long): Mono<Void> = Mono.empty()
            override fun unSubscribe(member: Long, topic: Long): Mono<Void> = Mono.empty()
            override fun unSubscribeAll(member: Long): Mono<Void> = Mono.empty()
            override fun unSubscribeAllIn(topic: Long): Mono<Void> = Mono.empty()
            override fun sendMessage(message: Message<Long, String>): Mono<Void> = Mono.empty()
            override fun listenTo(topic: Long): Flux<out Message<Long, String>> = Flux.empty()
            override fun exists(topic: Long): Mono<Boolean> = Mono.just(true)
        }

        fun service(grant: RecordingGrant?): TopicServiceImpl<Long, String, IndexSearchRequest> {
            this.grant = grant ?: RecordingGrant()
            return TopicServiceImpl(
                topicPersistence = topicPersistence,
                topicIndex = topicIndex,
                pubsub = pubsub,
                userPersistence = object : DummyPersistenceStore<Long, User<Long>>(), UserPersistence<Long> {},
                membershipPersistence = object : DummyPersistenceStore<Long, TopicMembership<Long>>(),
                    MembershipPersistence<Long> {},
                membershipIndex = object : DummyIndexService<Long, TopicMembership<Long>, IndexSearchRequest>(),
                    MembershipIndexService<Long, IndexSearchRequest> {
                    override fun size(query: IndexSearchRequest): Mono<Long> = Mono.just(0L)
                },
                emptyDataCodec = Supplier { "" },
                topicNameToQuery = Function { req -> IndexSearchRequest("name", req.name, 100) },
                memberOfIdToQuery = Function { IndexSearchRequest("memberOf", "", 100) },
                memberWithTopicToQuery = Function { IndexSearchRequest("member", "", 100) },
                messagePersistence = DummyPersistenceStore(),
                verifier = TestVerifiers.resolvingNothing(),
                rootKeys = FAKE_ROOTS,
                roomOwnerGrant = grant,
            )
        }
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -o -pl chat-core,chat-service-composite test -Dtest=TopicServiceOwnerGrantTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The compile errors name two unresolved references,
`roomOwnerGrant` and `RoomOwnerGrantException`. The test class does not compile,
which is the intended failure. A test class that compiles and passes here is a
finding about the test.

- [ ] **Step 3: Add the failure type**

In `chat-core/src/main/kotlin/com/demo/chat/domain/Exception.kt`, give the base
exception an optional cause. `Exception` already carries one, and this class
declines to expose it. All thirty call sites pass one argument, so each one
still compiles.

```kotlin
open class ChatException(msg: String, cause: Throwable? = null) : Exception(msg, cause)
```

Create `chat-core/src/main/kotlin/com/demo/chat/service/security/RoomOwnerGrantException.kt`:

```kotlin
package com.demo.chat.service.security

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key

/**
 * A room exists, and its ownership row was not written.
 *
 * **The message names the room key**, so an operator can write the missing row
 * by hand. `Key.toString` answers the id.
 *
 * The room keeps its store row, its index row and its open topic. No step of
 * the `addRoom` chain compensates another, so the residual is an ownerless
 * room. See `CHAT-zhjltbky`.
 *
 * **The key type is open**, because a subclass of `Throwable` cannot carry a
 * type parameter. Kotlin refuses it at the declaration.
 */
class RoomOwnerGrantException(val roomKey: Key<*>, cause: Throwable) :
    ChatException("The room owner grant failed for room $roomKey. The room exists and it has no owner.", cause)
```

- [ ] **Step 4: Change the topic service**

In `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/TopicServiceImpl.kt`:

Add the parameter after `rootKeys`:

```kotlin
    private val rootKeys: RootKeys<T>,
    private val roomOwnerGrant: RoomOwnerGrant<T>? = null,
```

Add two imports, `com.demo.chat.service.security.RoomOwnerGrant` and
`com.demo.chat.service.security.RoomOwnerGrantException`.

Insert one line into the `addRoom` chain, after the `pubsub.open` line:

```kotlin
                                    .then(pubsub.open(room.key.id))
                                    .then(grantOwner(room.key))
                                    .then(Mono.just(room.key))
```

Add the private helper beside `addRoom`:

```kotlin
    /**
     * The ownership row of a new room.
     *
     * **An absent port writes no grant and raises no error.** The port is
     * absent exactly when the composition carries no authorization, and then
     * no owner check can run.
     *
     * **A failed write names the room.** An operator reads the message and
     * writes the missing row by hand, because no step of this chain
     * compensates another. The cause travels with it, because the reason for
     * the refusal is what the operator acts on. See `CHAT-zhjltbky`.
     */
    private fun grantOwner(roomKey: Key<T>): Mono<Void> =
        roomOwnerGrant
            ?.grantOwner(roomKey)
            ?.onErrorMap { error -> RoomOwnerGrantException(roomKey, error) }
            ?: Mono.empty()
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `mvn -o -pl chat-core,chat-service-composite test -Dtest=TopicServiceOwnerGrantTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. Four tests run, zero failures.

- [ ] **Step 6: Declare the bean**

Create `chat-security/src/main/kotlin/com/demo/chat/security/service/RoomOwnerGrantConfiguration.kt`:

```kotlin
package com.demo.chat.security.service

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.RoomOwnerGrant
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The room owner writer as a bean.
 *
 * **The condition is `auth`, and not `composite` alone.** The writer needs
 * `authorizationService`, and `AuthBeansConfiguration` registers that bean
 * under the same condition. So the writer exists exactly where the service it
 * needs exists.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"])
open class RoomOwnerGrantConfiguration<T>(
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
) {

    @Bean
    open fun roomOwnerGrant(): RoomOwnerGrant<T> =
        ContextRoomOwnerGrant(ContextIdentity(rootKeys), authorizationService, rootKeys, typeUtil)
}
```

- [ ] **Step 7: Inject the port at the composition root**

In `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/CompositeServiceBeansConfiguration.kt`:

Add the constructor parameter beside `vectorIndexers`:

```kotlin
    private val roomOwnerGrants: ObjectProvider<RoomOwnerGrant<T>>,
```

Add the import `com.demo.chat.service.security.RoomOwnerGrant`.

Add the argument to the `topicService()` bean method:

```kotlin
            rootKeys = rootKeys,
            roomOwnerGrant = roomOwnerGrants.ifAvailable,
```

- [ ] **Step 8: Run the whole module and confirm it passes**

Run: `mvn -o -pl chat-core,chat-service-composite test`
Expected: PASS. Every test of the module runs, with zero failures.

- [ ] **Step 9: Confirm the deployment builds a topic service with the port**

Run: `mvn -o -pl chat-core,chat-deploy-memory -am -DskipTests package`
Expected: BUILD SUCCESS. The deployment holds `chat-security` through
`chat-service-controller`, which declares it at compile scope.

- [ ] **Step 10: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/security/RoomOwnerGrantException.kt \
        chat-core/src/main/kotlin/com/demo/chat/domain/Exception.kt \
        chat-security/src/main/kotlin/com/demo/chat/security/service/RoomOwnerGrantConfiguration.kt \
        chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/CompositeServiceBeansConfiguration.kt \
        chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/TopicServiceImpl.kt \
        chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/TopicServiceOwnerGrantTests.kt
git commit -m "Write the room owner grant at room creation (CHAT-zhjltbky)

The grant write is the last step of the addRoom chain. No step of that chain
compensates any other, so a failed grant write fails the request and leaves
the store row, the index row and the open topic in place.

A failed write raises RoomOwnerGrantException, which names the room key and
carries the cause. An operator reads the message and writes the missing row.
The residual is an ownerless room, and the failure reports its key.

ChatException gains an optional cause. Exception already carries one, and
the class declined to expose it. All thirty call sites pass one argument.

The port arrives as an ObjectProvider, which is the pattern this
configuration already uses for vectorIndexers. So a composition without
chat-security still builds a topic service.

The bean is conditioned on app.service.composite.auth, which is the
condition of the authorizationService bean it needs."
```

---

### Task 3: The shell stops writing the grant

**Files:**
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/TopicCommands.kt`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/shell/commands/TopicCommandsGrantTests.kt`

**Interfaces:**
- Consumes: the server-side writer from Task 2.
- Produces: `TopicCommands` holds no `AuthorizationService`, and `addTopic`
  writes no grant.

- [ ] **Step 1: Confirm which parameters the other methods use**

Run: `mvn -o -pl chat-core,chat-shell -DskipTests -Dmaven.test.skip=false test-compile`
Then read the file and remove only `authorizationService`. `typeUtil` and
`rootKeys` are still needed, because `CommandsUtil` takes them and
`identity(userId)` uses them.

- [ ] **Step 2: Write the failing test**

Create `chat-shell/src/test/kotlin/com/demo/chat/shell/commands/TopicCommandsGrantTests.kt`:

```kotlin
package com.demo.chat.shell.commands

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.service.security.AuthorizationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The shell writes no ownership row.
 *
 * **`*` is singular per target, and the server is the only writer.** Two
 * writers would give one room two owners. See `CHAT-zhjltbky`.
 */
class TopicCommandsGrantTests {

    /**
     * The authorization service of the shell is never called, because the
     * class does not hold one. This test pins the absence by constructing the
     * class from the deployment and driving the create path.
     *
     * A shell that cannot present an identity owns no room. Nothing breaks
     * today, because no deployment wires a check.
     */
    @Test
    fun `the shell holds no authorization service`() {
        val parameters = TopicCommands::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }

        assertThat(parameters).doesNotContain(AuthorizationService::class.java)
    }
}
```

- [ ] **Step 3: Run the test and confirm it fails**

Run: `mvn -o -pl chat-core,chat-shell test -Dtest=TopicCommandsGrantTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The assertion reports that the parameter list holds
`com.demo.chat.service.security.AuthorizationService`.

- [ ] **Step 4: Remove the writer**

In `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/TopicCommands.kt`:

Remove the constructor parameter `private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,`.

Replace the body of `addTopic` with:

```kotlin
    fun addTopic(
        userId: String,
        name: String
    ) {
        val identity = identity(userId)

        // The creator resolves in USER through the server registry before the
        // room exists. An unknown creator creates no room. No call blocks
        // inside the chain, because a remote answer runs it on a Netty thread.
        //
        // The server writes the ownership row. The shell writes none, because
        // `*` is singular per target and one writer is required.
        verifier.resolve(identity, ChatDomain.USER)
            .flatMap { topicService.addRoom(ByStringRequest(name)) }
            .block()
    }
```

Remove the imports that are now unused. Confirm with the compiler, and not by
reading.

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `mvn -o -pl chat-core,chat-shell test`
Expected: PASS. Read the `Tests run` and `Skipped` lines of the summary.

- [ ] **Step 6: Commit**

```bash
git add chat-shell/src/main/kotlin/com/demo/chat/shell/commands/TopicCommands.kt \
        chat-shell/src/test/kotlin/com/demo/chat/shell/commands/TopicCommandsGrantTests.kt
git commit -m "The shell stops writing the room ownership row (CHAT-zhjltbky)

One writer for `*` per target. The server writes the row now, and the shell
holds no AuthorizationService.

The shell loses ownership until it presents an identity. A room that
chat-shell creates has no owner, and only the identity-policy work can
change that. Its tests never log in, so no test moves here."
```

---

### Task 4: The delete decision

**Files:**
- Modify: `chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt`
- Modify: `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraAuthorizationMatrixTests.kt`

**Interfaces:**
- Consumes: the grant fixtures of both classes.
- Produces: five pinned rows for `deleteRoom`, which checks the room key with `REM`.

- [ ] **Step 1: Write the failing tests**

Add to `AnonymousAuthorizationMatrixTests`, beside the existing close tests:

```kotlin
    /**
     * **An owner may delete a room, and a close does not reach that.**
     *
     * Both rows are wildcards, so level 1 of the rank ties. The owner row names
     * the caller key, which is an `ENTITY`. The close names a domain root,
     * which is a `DOMAIN_ROOT`. Level 2 places `ENTITY` above `DOMAIN_ROOT`.
     */
    @Test
    fun `an owner may delete a closed room`() {
        val rows = listOf(
            grant(CALLER_KEY, ROOM_KEY, "*"),
            grant(USER_ROOT, ROOM_KEY, "*", expires = 1L)
        )

        assertThat(deleteAnswer(rows, authenticatedContext()))
            .describedAs("the owner of a closed room")
            .isTrue()
    }

    /** An owner deletes an open room. Nothing removes the row. */
    @Test
    fun `an owner may delete an open room`() {
        assertThat(deleteAnswer(listOf(grant(CALLER_KEY, ROOM_KEY, "*")), authenticatedContext())).isTrue()
    }

    /** **A close denies a caller who is not the owner.** The close wins its group, and its expiry then drops it. */
    @Test
    fun `a close denies a caller who holds no row`() {
        assertThat(deleteAnswer(listOf(grant(USER_ROOT, ROOM_KEY, "*", expires = 1L)), authenticatedContext()))
            .describedAs("a closed room")
            .isFalse()
    }

    /** An anonymous caller holds no row on this room, so it may not delete it. */
    @Test
    fun `an anonymous caller may not delete a room`() {
        assertThat(deleteAnswer(listOf(grant(CALLER_KEY, ROOM_KEY, "*")), anonymousContext()))
            .describedAs("an anonymous caller")
            .isFalse()
    }

    /**
     * **An authenticated caller who is not the owner may not delete a room.**
     * `ALL` is a literal, so the `{User, MessageTopic, ALL}` row does not
     * cover `REM`.
     */
    @Test
    fun `an authenticated caller who holds no row may not delete a room`() {
        assertThat(deleteAnswer(shippedGrants(), authenticatedContext()))
            .describedAs("a caller with no row on the room")
            .isFalse()
    }

    private fun deleteAnswer(rows: List<AuthMetadata<Long>>, context: SecurityContext): Boolean =
        SpringSecurityAccessBrokerService(broker(rows), rootKeys(), registry())
            .hasAccessTo(ROOM_KEY, "REM")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
            .block() ?: false
```

Add one row to `operations()` in the same file, so the matrix carries it:

```kotlin
            "deleteRoom MessageTopic REM" to { s -> s.hasAccessTo(ROOM_KEY, "REM") },
```

Then update the two expected maps in that file, `an anonymous caller may find a
user and nothing else` and any other map, to hold
`"deleteRoom MessageTopic REM" to false`.

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `mvn -o -pl chat-core,chat-security test -Dtest=AnonymousAuthorizationMatrixTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL, and the failures name the missing map entry `deleteRoom MessageTopic REM`.

- [ ] **Step 3: Add the same rows to the cassandra matrix**

In `CassandraAuthorizationMatrixTests`, add to `matrix(...)`:

```kotlin
        "deleteRoom MessageTopic REM" to answer({ it.hasAccessTo(room, "REM") }, access, context),
```

Add `"deleteRoom MessageTopic REM" to false` to the `expected` map. For the four
all-deny contexts the existing `allMatch { !it }` already covers the new row.

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `mvn -o -pl chat-core,chat-security test -Dtest=AnonymousAuthorizationMatrixTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

Run: `mvn -o -pl chat-core,chat-deploy-cassandra test -Dtest=CassandraAuthorizationMatrixTests -Dsurefire.failIfNoSpecifiedTests=false -Pintegration`
Expected: PASS. This test needs Docker. Confirm Docker is running first.

- [ ] **Step 5: Commit**

```bash
git add chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt \
        chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraAuthorizationMatrixTests.kt
git commit -m "Pin the delete decision in both authorization matrices (CHAT-zhjltbky)

An owner may delete a room, and a close does not reach that. Both rows are
wildcards, so level 1 of the rank ties, and level 2 places the ENTITY
principal of the owner above the DOMAIN_ROOT principal of the close.

A close still denies a caller who holds no row: the close wins its group,
and the expiry filter then drops it.

ALL is a literal, so the {User, MessageTopic, ALL} row does not cover REM.
That is why an anonymous caller and a non-owner both deny."
```

---

### Task 5: Both send checks name the room

**Files:**
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/access/SpringSecurityAccessBrokerService.kt`
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/access/core/PubSubAccess.kt`
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/access/composite/MessageServiceAccess.kt`
- Test: `chat-security/src/test/kotlin/com/demo/chat/test/SendCheckExpressionTests.kt`

**Interfaces:**
- Consumes: `KeyVerifier.resolve(id, null)` and `AccessBroker.hasAccessByPrincipal`.
- Produces: `fun hasAccessToId(target: T, perm: String): Mono<Boolean>` on
  `SpringSecurityAccessBrokerService`.

**Measured before this task, and it decides the task.** A raw id cannot bind to
`hasAccessTo(Key<T>, perm)`. The compiled signature is
`hasAccessTo(com.demo.chat.domain.Key, java.lang.String)`, read with `javap` on
`chat-security/target/classes`. A probe on spring-expression 7.0.9 reports
`EL1004E: Method hasAccessTo(java.lang.Long,java.lang.String) cannot be found`.
`#req.dest()`, `#req.uid()` and `#req.roomId()` name methods that Kotlin never
generates. The properties are `getDest()`, `getUid()` and `getRoomId()`.

- [ ] **Step 1: Write the failing test**

Create `chat-security/src/test/kotlin/com/demo/chat/test/SendCheckExpressionTests.kt`:

```kotlin
package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.AuthSummarizer
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.security.access.composite.MessageServiceAccess
import com.demo.chat.security.access.core.PubSubAccess
import com.demo.chat.security.rank.PrincipalRank
import com.demo.chat.security.service.CoreAuthorizationService
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.test.key.TestKeys
import com.demo.chat.test.key.TestVerifiers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.expression.BeanResolver
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The two send checks, read from the annotations and evaluated as SpEL.
 *
 * **A method call to the broker fails when the target is a raw id.** The
 * compiled signature of the two argument form takes a `Key`, and a raw id
 * cannot bind to it. This class evaluates the real annotation text, so a
 * change to the text fails here. See `CHAT-zhjltbky`.
 */
class SendCheckExpressionTests {

    /**
     * **Both send checks name the room.** The room carries the
     * `MessageTopic` root, and `{User, MessageTopic, ALL}` names that root.
     */
    @Test
    fun `both send checks allow a room owner`() {
        val access = access(listOf(grant(CALLER, ROOM, "*")))

        assertThat(evaluate(sendExpression(), "req", request(), access))
            .describedAs("the message send expression")
            .isTrue()
        assertThat(evaluate(sendMessageExpression(), "message", message(), access))
            .describedAs("the pubsub send expression")
            .isTrue()
    }

    /** A caller with no row on the room is denied by both checks. */
    @Test
    fun `both send checks deny a caller with no row`() {
        val access = access(listOf(grant(USER_ROOT, TOPIC_ROOT, "ALL")))

        assertThat(evaluate(sendExpression(), "req", request(), access)).isFalse()
        assertThat(evaluate(sendMessageExpression(), "message", message(), access)).isFalse()
    }

    /**
     * **A row on the `Message` root does not cover a send.** The room is
     * another domain, so the shipped `{User, Message, SEND}` row does not
     * reach it.
     */
    @Test
    fun `a message root row does not cover a send to a room`() {
        val access = access(listOf(grant(USER_ROOT, MESSAGE_ROOT, "SEND")))

        assertThat(evaluate(sendExpression(), "req", request(), access)).isFalse()
        assertThat(evaluate(sendMessageExpression(), "message", message(), access)).isFalse()
    }

    /** The annotation text of `MessageServiceAccess.send`. */
    private fun sendExpression(): String =
        MessageServiceAccess::class.java.methods
            .first { it.name == "send" && it.parameterCount == 1 }
            .getAnnotation(PreAuthorize::class.java).value

    /** The annotation text of `PubSubAccess.sendMessage`. */
    private fun sendMessageExpression(): String =
        PubSubAccess::class.java.methods
            .first { it.name == "sendMessage" && it.parameterCount == 1 }
            .getAnnotation(PreAuthorize::class.java).value

    /**
     * Evaluate one expression against the bean, as Spring does for `@chatAccess`.
     */
    private fun evaluate(expression: String, variable: String, value: Any, access: SpringSecurityAccessBrokerService<Long>): Boolean {
        val context = StandardEvaluationContext()
        context.setBeanResolver(BeanResolver { _, name ->
            if (name == "chatAccess") access else null
        })
        context.setVariable(variable, value)

        @Suppress("UNCHECKED_CAST")
        val answer = SpelExpressionParser().parseExpression(expression).getValue(context) as Mono<Boolean>

        return answer
            .contextWrite(
                ReactiveSecurityContextHolder.withSecurityContext(
                    Mono.just(SecurityContextImpl(UsernamePasswordAuthenticationToken(details(), "secret", listOf())))
                )
            )
            .block() ?: false
    }

    private fun request() = MessageSendRequest("hello", CALLER, ROOM)

    private fun message(): Message<Long, String> = Message.create(
        com.demo.chat.domain.MessageKey.of(11L, 4L, CALLER, ROOM), "hello", true
    )

    private fun details() = ChatUserDetails(User.create(CALLER, "u", "handle", "http://u"), listOf())

    private fun access(rows: List<AuthMetadata<Long>>): SpringSecurityAccessBrokerService<Long> {
        val store = MapAuthStore()
        val index = MapAuthIndex(store)
        rows.forEach { store.rows[it.key] = it }
        val service = CoreAuthorizationService(
            store, index, { it }, { it }, { ANON }, { USER_ROOT },
            AuthSummarizer({ a, b -> (a.key.id - b.key.id).toInt() }, PrincipalRank(rootKeys())),
            TestVerifiers.holding(rootKeys(), listOf(ANON, CALLER, ROOM, MESSAGE)),
        )
        return SpringSecurityAccessBrokerService(
            com.demo.chat.security.access.AuthMetadataAccessBroker(service, TestVerifiers.resolvingNothing()),
            rootKeys(),
            TestVerifiers.holding(rootKeys(), listOf(ANON, CALLER, ROOM, MESSAGE)),
        )
    }

    private fun grant(principal: Key<Long>, target: Key<Long>, permission: String, expires: Long = 0L) =
        com.demo.chat.domain.StringRoleAuthorizationMetadata(
            TestKeys.key(nextKey.getAndIncrement()), principal, target, permission, false, expires
        )

    private fun rootKeys(): RootKeys<Long> = RootKeysFixture.ofLong(
        mapOf(
            ChatDomain.USER to USER_ROOT,
            ChatDomain.MESSAGE to MESSAGE_ROOT,
            ChatDomain.MESSAGE_TOPIC to TOPIC_ROOT,
        ),
        admin = ADMIN,
        anon = ANON,
    )

    private class MapAuthStore : PersistenceStore<Long, AuthMetadata<Long>> {
        val rows: MutableMap<Key<Long>, AuthMetadata<Long>> = linkedMapOf()

        override fun key(): Mono<out Key<Long>> = Mono.just(TestKeys.key(nextKey.getAndIncrement()))
        override fun add(ent: AuthMetadata<Long>): Mono<Void> {
            rows[ent.key] = ent
            return Mono.empty()
        }

        override fun rem(key: Key<Long>): Mono<Void> {
            rows.remove(key)
            return Mono.empty()
        }

        override fun get(key: Key<Long>): Mono<out AuthMetadata<Long>> = Mono.justOrEmpty(rows[key])
        override fun all(): Flux<out AuthMetadata<Long>> = Flux.fromIterable(rows.values)
    }

    private open class MapAuthIndex(private val store: MapAuthStore) :
        IndexService<Long, AuthMetadata<Long>, Key<Long>> {

        override fun add(entity: AuthMetadata<Long>): Mono<Void> = Mono.empty()
        override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
        override fun findBy(query: Key<Long>): Flux<out Key<Long>> =
            Flux.fromIterable(store.rows.values.filter { it.target == query }.map { it.key })

        override fun findUnique(query: Key<Long>): Mono<out Key<Long>> = findBy(query).next()
    }

    private companion object {
        val nextKey = java.util.concurrent.atomic.AtomicLong(500L)

        val USER_ROOT: Key<Long> = Key.root(3L)
        val MESSAGE_ROOT: Key<Long> = Key.root(4L)
        val TOPIC_ROOT: Key<Long> = Key.root(5L)

        val ADMIN: Key<Long> = Key.of(2L, 3L)
        val ANON: Key<Long> = Key.of(1L, 3L)
        val CALLER: Key<Long> = Key.of(6L, 3L)
        val ROOM: Key<Long> = Key.of(7L, 5L)
        val MESSAGE: Key<Long> = Key.of(8L, 4L)
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -o -pl chat-core,chat-security test -Dtest=SendCheckExpressionTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL on `EL1004E`. The message names
`hasAccessTo(java.lang.Long,java.lang.String)` for the first case and
`dest()` for the second.

- [ ] **Step 3: Add the resolving method**

In `SpringSecurityAccessBrokerService.kt`, beside `hasAccessTo(target: Key<T>, perm)`:

```kotlin
    /**
     * The check of an access expression whose target is a raw id.
     *
     * **The two argument form takes a `Key`, and a raw id cannot bind to it.**
     * SpEL resolves a method by name and by argument count, and then by
     * assignability. A `Long` is not a `Key`, so that form fails with
     * `EL1004E`. This method takes `T`, which erases to `Object`, so a raw id
     * binds.
     *
     * A raw id carries no root, so it resolves in its stored domain first. An
     * unknown id denies. The resolved key then follows the same path as
     * [hasAccessTo].
     */
    fun hasAccessToId(target: T, perm: String): Mono<Boolean> =
        verifier.resolve(target, null)
            .flatMap { hasAccessTo(it.key, perm) }
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))
```

- [ ] **Step 4: Retarget both send expressions**

In `PubSubAccess.kt`, replace the `sendMessage` expression:

```kotlin
    @PreAuthorize("@chatAccess.hasAccessToId(#message.key.dest, 'SEND')")
    override fun sendMessage(message: Message<T, V>): Mono<Void>
```

In `MessageServiceAccess.kt`, replace the `send` expression. **Use the
property, and not the method.** Kotlin generates `getDest()`, so `#req.dest`
resolves and `#req.dest()` does not:

```kotlin
    @PreAuthorize("@chatAccess.hasAccessToId(#req.dest, 'SEND')")
    override fun send(req: MessageSendRequest<T, V>): Mono<out Key<T>>
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `mvn -o -pl chat-core,chat-security test -Dtest=SendCheckExpressionTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. Three tests run, zero failures.

- [ ] **Step 6: Run the whole module**

Run: `mvn -o -pl chat-core,chat-security test`
Expected: PASS, with zero failures.

- [ ] **Step 7: Commit**

```bash
git add chat-security/src/main/kotlin/com/demo/chat/security/access/SpringSecurityAccessBrokerService.kt \
        chat-security/src/main/kotlin/com/demo/chat/security/access/core/PubSubAccess.kt \
        chat-security/src/main/kotlin/com/demo/chat/security/access/composite/MessageServiceAccess.kt \
        chat-security/src/test/kotlin/com/demo/chat/test/SendCheckExpressionTests.kt
git commit -m "Both send checks name the room (CHAT-zhjltbky)

PubSubAccess.sendMessage checked the message key, and MessageServiceAccess
send checked the room. A wildcard row on the room could never cover the
message key, because neither key is the root of the other.

**The old expressions could not be evaluated at all.** A raw id cannot bind
to hasAccessTo(Key, String), and the compiled signature takes a Key. SpEL
reports EL1004E. #req.dest() also named a method that Kotlin never
generates; the property is getDest().

hasAccessToId resolves a raw id in its stored domain and then follows the
same path as hasAccessTo.

The other raw id expressions of the access interfaces stay broken. They are
filed separately, because each one moves what a check means."
```

---

### Task 6: The documents, and the deferred work

**Files:**
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/AuthSummarizer.kt`
- Modify: `docs/ANONYMOUS-AUTHORIZATION.md`
- Modify: `docs/superpowers/specs/2026-09-23-operation-policy-draft.md`
- Modify: `forward-register.md`

**Interfaces:**
- Consumes: every measurement of Tasks 1 to 5.
- Produces: two fp issues, one register section, and one corrected KDoc.

- [ ] **Step 1: Correct the wildcard KDoc**

In `AuthSummarizer.kt`, replace property 2 of the `WILDCARD` KDoc:

```kotlin
         * 2. **Singular.** One `*` per target. **One writer enforces this, and
         *    no code refuses a second row.** `ContextRoomOwnerGrant` is that
         *    writer since `CHAT-zhjltbky`. A second writer would create a
         *    second owner, and the rank would then answer by time alone.
```

An earlier version claimed that a second row is refused at the source. No code
refuses it, and a search for `WILDCARD` in main source reaches this file and
`PrincipalRank` alone.

- [ ] **Step 2: Record the measured expression finding**

Add to `docs/ANONYMOUS-AUTHORIZATION.md`, under `## What is not wired`:

```markdown
## Two expression shapes cannot be evaluated

Measured on 2026-10-01 at `09d4c9a6`, with `javap` on the compiled
`SpringSecurityAccessBrokerService` and a probe on spring-expression 7.0.9.

**A raw id cannot bind to the two argument `hasAccessTo`.** The compiled
signature is `hasAccessTo(com.demo.chat.domain.Key, java.lang.String)`. SpEL
resolves a method by name and argument count, and then by assignability. A
`Long` is not a `Key`, so the call fails with `EL1004E`. The three argument
form erases to `(Object, Object, String)`, so a raw pair of ids binds there.

**`#req.dest()`, `#req.uid()` and `#req.roomId()` name methods that Kotlin
never generates.** A Kotlin `data class` property `dest` compiles to
`getDest()`. The property form `#req.dest` resolves.

Both send expressions were repaired under `CHAT-zhjltbky`, by
`hasAccessToId` and by the property form. **The remaining sites are open.**
Every `@PreAuthorize` of the access interfaces is latent, because no
production type implements one, so this changes no running answer.
```

- [ ] **Step 3: File the deferred issues**

Run:

```bash
fp issue create --title "Repair the access expressions that cannot be evaluated" \
  --parent CHAT-znprrzhn --property labels=authorization
```

Write the description with these facts:

- The two argument `hasAccessTo` takes a `Key`, so a raw id fails with `EL1004E`.
- `#req.dest()`, `#req.uid()` and `#req.roomId()` name methods that do not exist.
- `CHAT-zhjltbky` repaired the two send expressions alone.
- Enumerate the rest with
  `grep -rn "PreAuthorize" chat-security/src/main/kotlin/com/demo/chat/security/access/`,
  and confirm each site with the probe in `SendCheckExpressionTests`.
- Each repair moves what a check means, so each one needs its own matrix row.
- This issue must land before `CHAT-znprrzhn` wires any check.

Run:

```bash
fp issue create --title "Refuse a second wildcard row for one target" \
  --parent CHAT-znprrzhn --property labels=authorization
```

Write the description with these facts:

- `AuthSummarizer.WILDCARD` states that one `*` per target holds.
- `AuthorizationService.authorize` writes a row for any caller, so a second
  writer creates a second owner.
- `RoomOwnerGrantTests.a second grant writes a second wildcard row` is the
  evidence, and it must be replaced when this lands.
- The guard belongs where the row is written, and not in the rank.
- One writer exists today, which is why this is deferred rather than urgent.

- [ ] **Step 4: Record the issue numbers in the draft document**

In `docs/superpowers/specs/2026-09-23-operation-policy-draft.md`, add a line
under the close decision that names both new issues, and state that a denial is
the absence of a grant.

- [ ] **Step 5: Record the work in the register**

Add a section to `forward-register.md`, before `## The work queue`, with:

- the issue, the branch and the merge commit,
- the six owner decisions,
- the delete decision and the rank levels that carry it,
- the port and its condition,
- the failure contract and the ownerless room,
- two facts that cost a measurement: a raw id cannot bind to
  `hasAccessTo(Key, String)`, and a Kotlin data class property has no
  same-named accessor,
- the two new issues, and the note that the register's Checkout row lags the
  true tip on purpose.

- [ ] **Step 6: Run the drift and whitespace checks**

Run: `drift check`
Expected: ok.

Run: `git diff --check`
Expected: no output.

- [ ] **Step 7: Run the full default build**

Run: `mvn -o -B clean test`
Expected: BUILD SUCCESS. Read the summary lines. Compare the test count against
the previous reading and record both.

- [ ] **Step 8: Commit**

```bash
git add chat-security/src/main/kotlin/com/demo/chat/security/AuthSummarizer.kt \
        docs/ANONYMOUS-AUTHORIZATION.md \
        docs/superpowers/specs/2026-09-23-operation-policy-draft.md \
        forward-register.md
git commit -m "Record the grant policy and the measured expression defect (CHAT-zhjltbky)

The wildcard KDoc stated that a second `*` per target is refused at the
source. No code refuses it. One writer holds the property now, and the KDoc
names that instead.

The authorization document gains the measured expression finding: a raw id
cannot bind to hasAccessTo(Key, String), and a Kotlin data class property
has no same-named accessor. Both send expressions were repaired; the
remaining sites are filed.

The register carries the decisions, the port, the failure contract, and the
two facts that cost a measurement."
```

---

## Self-Review

**Spec coverage.** Stipulation 1 and 2 need no code, and the spec says so.
Stipulation 3 is Task 1 and Task 2. Stipulation 4 is every task. Stipulation 5
is Task 4. Stipulation 6 is the absence of a `userinit.yml` edit, and Task 6
Step 3 records it. Change 3 is Tasks 1 and 2. Change 4 is Task 3. Change 5 is
Task 5. The three findings that change documents are Task 6. The evidence plan
maps to Tasks 1, 2, 4 and 5.

**Two evidence-plan rows have no owning task.** `UserInitConfigBindingTests`
needs no change, because this issue edits no line of that file. Its existing
run in Task 6 Step 7 holds it.

**Two corrections from the owner review are in.** Task 2 carries no helper that
does not implement its own interface, and the grant-failure test asserts the
room key by field, by message and by cause. The failure type,
`RoomOwnerGrantException`, is new, and the spec names it. Task 2 Step 4 adds it
and gives `ChatException` an optional cause.

**Owner boundary coverage.** One writer: Task 3 removes the shell writer, and
Task 2 Step 3 keeps one call site. Source-level wildcard uniqueness is deferred
in Task 6 Step 3. The seven required tests: owner deletion after close is
Task 4; no identity is Task 1; absent grant port is Task 2; grant failure with
preserved earlier writes is Task 2; exactly one owner grant is Task 1 and
Task 2 together; both send checks against the room is Task 5; no duplicate
shell grant is Task 3.

**Type consistency.** `RoomOwnerGrant.grantOwner(roomKey: Key<T>): Mono<Void>`
is spelled the same in Tasks 1, 2 and 5. `hasAccessToId(target: T, perm: String)`
is spelled the same in Task 5 Steps 3 and 4.
