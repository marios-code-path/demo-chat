package com.demo.chat.test

import com.demo.chat.test.key.verified

import com.demo.chat.test.key.TestVerifiers

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.StringRoleAuthorizationMetadata
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.Admin
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.AuthSummarizer
import com.demo.chat.security.rank.PrincipalRank
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.AuthMetadataAccessBroker
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.security.service.CoreAuthorizationService
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.PersistenceStore
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
import java.util.concurrent.atomic.AtomicLong

/**
 * What the shipped grants allow, per caller and per operation.
 *
 * **This wires the real authorization stack.** `CoreAuthorizationService`,
 * `AuthSummarizer`, `AuthMetadataAccessBroker` and
 * `SpringSecurityAccessBrokerService` are the production classes. Only the
 * store and the index are replaced, by maps.
 *
 * The grants come from
 * `shared-deploy-configuration/src/main/config/userinit.yml`, which every
 * deployment loads.
 *
 * The operations are the `@PreAuthorize` expressions of the access
 * interfaces in `chat-security`, copied exactly.
 *
 * See `docs/IDENTITY-POLICY.md` and `docs/ANONYMOUS-AUTHORIZATION.md`.
 */
class AnonymousAuthorizationMatrixTests {

    /**
     * **An anonymous caller may read a user and a message.**
     *
     * `userinit.yml` grants the `Anon` key `User:FIND`, `User:PUT` and
     * `Message:GET`. The two `User` grants reach `whoami`. `Message:GET` names
     * the `Message` root, and `messageById` checks one message key. Since
     * `CHAT-rfzsnbco` the check reads that root, so the row applies.
     */
    @Test
    fun `an anonymous caller may find a user and nothing else`() {
        assertThat(matrixFor(anonymousContext())).isEqualTo(
            mapOf(
                "addRoom MessageTopic NEW" to false,
                "send room SEND" to false,
                "whoami User FIND" to true,
                "messageById GET" to true,
                "listRooms MessageTopic ALL" to true,
                "addUser User NEW" to false,
                "deleteRoom MessageTopic REM" to false
            )
        )
    }

    /**
     * **An authenticated caller reaches the same answers as an anonymous
     * one.** `CoreAuthorizationService` puts the `Anon` key in the actor set
     * of every query, so an anonymous grant is a floor for every caller.
     *
     * The five `user: User` rows of `userinit.yml` name the `User` root key as
     * the principal. `CHAT-mahevldm` puts that key in the actor set of every
     * query, because every caller is a user. So all five reach every caller.
     */
    @Test
    fun `an authenticated caller reaches the same answers`() {
        assertThat(matrixFor(authenticatedContext())).isEqualTo(matrixFor(anonymousContext()))
    }

    @Test
    fun `an unauthenticated caller is denied everything`() {
        assertThat(matrixFor(unauthenticatedContext()).values).allMatch { !it }
    }

    @Test
    fun `an unsupported principal is denied everything`() {
        assertThat(matrixFor(unsupportedContext()).values).allMatch { !it }
    }

    @Test
    fun `a caller with no context is denied everything`() {
        assertThat(matrixFor(null).values).allMatch { !it }
    }

    /**
     * The only expiry this application has is on the grant.
     * `AuthSummarizer` keeps a row when `expires` is 0 or in the future.
     */
    @Test
    fun `an expired grant does not allow`() {
        val expired = grant(ANON_KEY, USER_ROOT, "FIND", System.currentTimeMillis() - 60_000L)

        assertThat(allowedWith(listOf(expired), anonymousContext())).isFalse()
    }

    /**
     * A wildcard row names every permission. The check asks one permission, so
     * the row must answer that permission. See the close decision in
     * `docs/superpowers/specs/2026-09-23-operation-policy-draft.md`.
     */
    @Test
    fun `a live wildcard grant allows a permission that no row names`() {
        val wildcard = grant(ANON_KEY, USER_ROOT, "*")

        assertThat(allowedWith(listOf(wildcard), anonymousContext())).isTrue()
    }

    /**
     * This is the close case. A close writes one expired wildcard row, and that
     * row must remove the permissions that came before it.
     */
    @Test
    fun `an expired wildcard grant removes a permission that an earlier row granted`() {
        val granted = grant(ANON_KEY, USER_ROOT, "FIND")
        val closed = grant(ANON_KEY, USER_ROOT, "*", System.currentTimeMillis() - 60_000L)

        assertThat(allowedWith(listOf(granted, closed), anonymousContext())).isFalse()
    }

    @Test
    fun `a grant that has not expired allows`() {
        val live = grant(ANON_KEY, USER_ROOT, "FIND", System.currentTimeMillis() + 600_000L)

        assertThat(allowedWith(listOf(live), anonymousContext())).isTrue()
    }

    /**
     * **A row that names the `User` root reaches every caller.** Every caller
     * is a user, so the actor set carries the `User` root beside the anonymous
     * key. See `CHAT-mahevldm`.
     *
     * This is the single target path, which is `getAuthorizationsAgainst`.
     */
    @Test
    fun `a row naming the User root reaches a caller through one target`() {
        val row = grant(USER_ROOT, TOPIC_ROOT, "ALL")
        val service = SpringSecurityAccessBrokerService(broker(listOf(row)), rootKeys(), registry())

        val answer = service.hasAccessToDomain("MessageTopic", "ALL")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(authenticatedContext())))
            .block() ?: false

        assertThat(answer).isTrue()
    }

    /**
     * The many target path, which is `permittedTargets`. It evaluates each
     * target through the single target path since `CHAT-wkwiipgy`.
     */
    @Test
    fun `a row naming the User root reaches a caller through many targets`() {
        val row = grant(USER_ROOT, TOPIC_ROOT, "ALL")

        assertThat(permitted(broker(listOf(row)), listOf(TOPIC_ROOT), "ALL")).containsExactly(TOPIC_ROOT)
    }

    /**
     * **A self grant reaches its owner and nobody else.** `userinit.yml` grants
     * `Admin` the wildcard on `Admin`. The actor set held the target key until
     * `CHAT-ixzpkqxg`, so that row passed the actor filter for every caller that
     * asked about `Admin`.
     */
    @Test
    fun `a self grant does not reach a third caller`() {
        val broker = broker(shippedGrants())

        assertThat(broker.hasAccessByKey(CALLER_KEY, ADMIN_KEY.verified(), "GET").block()).isFalse()
        assertThat(permitted(broker, listOf(ADMIN_KEY), "GET")).isEmpty()
    }

    /**
     * **A key holds every right over itself, and no row is needed.** The rule is
     * in the broker, so the administrator reaches `Admin` with no grant at all.
     */
    @Test
    fun `the administrator holds every right over itself with no row`() {
        val broker = broker(listOf())

        assertThat(broker.hasAccessByKey(ADMIN_KEY, ADMIN_KEY.verified(), "GET").block()).isTrue()
    }

    /** The same rule reaches the caller of the security context. */
    @Test
    fun `an authenticated caller holds every right over itself`() {
        val service = SpringSecurityAccessBrokerService(broker(listOf()), rootKeys(), registry())

        val answer = service.hasAccessTo(CALLER_KEY, "DEL")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(authenticatedContext())))
            .block() ?: false

        assertThat(answer).isTrue()
    }

    /**
     * **Self authority is absolute.** A close is an expired wildcard on a domain
     * root principal, and it removes every named grant. It does not remove the
     * authority of a key over itself.
     */
    @Test
    fun `a close does not remove self authority`() {
        val close = grant(USER_ROOT, CALLER_KEY, "*", expires = 1L)

        assertThat(broker(listOf(close)).hasAccessByKey(CALLER_KEY, CALLER_KEY.verified(), "GET").block()).isTrue()
    }

    /**
     * **Self authority permits the caller's own target and no other.** The room
     * has no grant, so the answer holds the caller alone. See `CHAT-wkwiipgy`.
     */
    @Test
    fun `self authority permits only the caller's own target`() {
        val broker = broker(listOf())

        assertThat(permitted(broker, listOf(CALLER_KEY), "GET")).containsExactly(CALLER_KEY)
        assertThat(permitted(broker, listOf(CALLER_KEY, ROOM_KEY), "GET")).containsExactly(CALLER_KEY)
    }

    /**
     * **Each target is evaluated on its own.** One permitted target does not
     * permit the list. Until `CHAT-wkwiipgy` the many target check read every
     * row of every target into one set, so one grant allowed the whole list.
     */
    @Test
    fun `a mixed list answers the permitted targets alone`() {
        val broker = broker(listOf(grant(CALLER_KEY, ROOM_KEY, "GET")))

        assertThat(permitted(broker, listOf(ROOM_KEY, MESSAGE_KEY), "GET")).containsExactly(ROOM_KEY)
        assertThat(permitted(broker, listOf(MESSAGE_KEY, ROOM_KEY), "GET")).containsExactly(ROOM_KEY)
    }

    /** An empty list, and a list with no permitted target, both answer nothing. */
    @Test
    fun `an empty list and a fully denied list answer nothing`() {
        val broker = broker(listOf(grant(CALLER_KEY, ROOM_KEY, "GET")))

        assertThat(permitted(broker, listOf(), "GET")).isEmpty()
        assertThat(permitted(broker, listOf(MESSAGE_KEY, ADMIN_KEY), "GET")).isEmpty()
    }

    /** An entity with no target denies, whatever the grants hold. */
    @Test
    fun `an entity with no target denies`() {
        val service = SpringSecurityAccessBrokerService(broker(shippedGrants()), rootKeys(), registry())

        val answer = service.hasAccessToEntity("not an entity", "GET", ChatDomain.USER)
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(authenticatedContext())))
            .block()

        assertThat(answer).isFalse()
    }

    /**
     * **A grant on a domain root covers an object of that domain.**
     * `messageById` checks one message key with `GET`. The domain root of a
     * message key is the `Message` root, and `{Anon, Message, GET}` names that
     * root. `CHAT-rfzsnbco` makes the check read the root.
     */
    @Test
    fun `a message read allows through a domain root row`() {
        val row = grant(ANON_KEY, MESSAGE_ROOT, "GET")
        val service = SpringSecurityAccessBrokerService(broker(listOf(row)), rootKeys(), registry())

        val answer = service.hasAccessTo(MESSAGE_KEY, "GET")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(anonymousContext())))
            .block() ?: false

        assertThat(answer).describedAs("a message read").isTrue()
    }

    /**
     * **A room read allows through a domain root row.** The domain root of a
     * room key is the `MessageTopic` root, and `{User, MessageTopic, GET}`
     * names that root.
     */
    @Test
    fun `a room read allows through a domain root row`() {
        val row = grant(USER_ROOT, TOPIC_ROOT, "GET")
        val service = SpringSecurityAccessBrokerService(broker(listOf(row)), rootKeys(), registry())

        val answer = service.hasAccessTo(ROOM_KEY, "GET")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(authenticatedContext())))
            .block() ?: false

        assertThat(answer).describedAs("a room read").isTrue()
    }

    /**
     * **The scan does not widen across domains.** The domain root of a room key
     * is the `MessageTopic` root. The shipped `{User, Message, SEND}` row names
     * the `Message` root, which is a different domain. So `send` stays denied.
     */
    @Test
    fun `a send stays denied because the row names another domain`() {
        val rows = listOf(
            grant(USER_ROOT, MESSAGE_ROOT, "SEND"),
            grant(USER_ROOT, TOPIC_ROOT, "ALL")
        )
        val service = SpringSecurityAccessBrokerService(broker(rows), rootKeys(), registry())

        val answer = service.hasAccessTo(ROOM_KEY, "SEND")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(authenticatedContext())))
            .block() ?: false

        assertThat(answer).describedAs("a send to a room").isFalse()
    }

    /**
     * **A check that already names a domain root reads one target.** A root key
     * is its own root, so the scan adds nothing. The index records the key it
     * was asked for, and not a count.
     */
    @Test
    fun `a domain root check reads one target`() {
        val auth = recordingAuth(shippedGrants())

        auth.broker.hasAccessByKey(CALLER_KEY, TOPIC_ROOT.verified(), "ALL").block()

        assertThat(auth.index.asked).containsExactly(TOPIC_ROOT)
    }

    /**
     * **A check on one object reads the object and its domain root.** The room
     * key carries the `MessageTopic` root, so the check reads both.
     */
    @Test
    fun `an object check reads the object and its domain root`() {
        val auth = recordingAuth(shippedGrants())

        auth.broker.hasAccessByKey(CALLER_KEY, ROOM_KEY.verified(), "GET").block()

        assertThat(auth.index.asked).containsExactly(ROOM_KEY, TOPIC_ROOT)
    }

    /**
     * **Owner selection reads the exact target.** A wildcard row on the domain
     * root must not enter the selection for one room. A domain root read there
     * would give one target two owners, which is the rule the owner set on
     * 2026-09-24.
     */
    @Test
    fun `owner selection reads the exact target alone`() {
        val named = grant(USER_ROOT, ROOM_KEY, "GET")
        val ownerRow = grant(USER_ROOT, TOPIC_ROOT, "*")
        val auth = recordingAuth(listOf(named, ownerRow))

        val selected = auth.service.getAuthorizationsForTarget(ROOM_KEY).collectList().block()!!

        assertThat(auth.index.asked).describedAs("the targets the selection read").containsExactly(ROOM_KEY)
        assertThat(selected.map { it.target }).describedAs("the targets it selected").containsExactly(ROOM_KEY)
    }

    /**
     * **Self authority answers before any read.** The rule is in the broker, so
     * a check of a key against itself must leave the index unread.
     */
    @Test
    fun `a check of a key against itself reads no target`() {
        val auth = recordingAuth(shippedGrants())

        val answer = auth.broker.hasAccessByKey(CALLER_KEY, CALLER_KEY.verified(), "GET").block()

        assertThat(answer).isTrue()
        assertThat(auth.index.asked).isEmpty()
    }

    /**
     * **A check with no permission still reads both targets.** A null
     * permission lists the rows as they are stored, and the scan is not part of
     * that decision.
     */
    @Test
    fun `a check with no permission reads both targets`() {
        val auth = recordingAuth(shippedGrants())

        auth.service.getAuthorizationsAgainst(CALLER_KEY, ROOM_KEY, null).collectList().block()

        assertThat(auth.index.asked).containsExactly(ROOM_KEY, TOPIC_ROOT)
    }

    /**
     * **A root that no domain holds is read as a target too.** The scan trusts
     * the root of the key, which the boundary verified. A root outside the
     * registry matches no row, and it does not throw.
     */
    @Test
    fun `a target with an unregistered root reads it and matches nothing`() {
        val alien = Key.of(9L, 404L)
        val auth = recordingAuth(shippedGrants())

        val rows = auth.service.getAuthorizationsAgainst(CALLER_KEY, alien, "GET").collectList().block()!!

        assertThat(auth.index.asked).containsExactly(alien, Key.root(404L))
        assertThat(rows).isEmpty()
    }

    /**
     * **A many target request mixes one object and one domain root.** The
     * request holds a room and the `MessageTopic` root. Each entry is expanded
     * on its own, so the room reads two targets and the root reads one.
     */
    @Test
    fun `a mixed many target request expands each entry alone`() {
        val auth = recordingAuth(listOf(grant(CALLER_KEY, TOPIC_ROOT, "GET")))

        auth.service.getAuthorizationsAgainstMany(CALLER_KEY, listOf(ROOM_KEY, TOPIC_ROOT), "GET")
            .collectList().block()!!

        assertThat(auth.index.asked).containsExactly(ROOM_KEY, TOPIC_ROOT, TOPIC_ROOT)
    }

    /** The same request, through the broker, permits both targets. */
    @Test
    fun `a mixed many target request permits the object and the root`() {
        val broker = broker(listOf(grant(CALLER_KEY, TOPIC_ROOT, "GET")))

        assertThat(permitted(broker, listOf(ROOM_KEY, TOPIC_ROOT), "GET"))
            .containsExactly(ROOM_KEY, TOPIC_ROOT)
    }

    /**
     * **A repeated target is read once per occurrence.** The many path expands
     * each entry alone, and it de-duplicates nothing across entries.
     */
    @Test
    fun `a repeated target is read once per occurrence`() {
        val auth = recordingAuth(shippedGrants())

        auth.service.getAuthorizationsAgainstMany(CALLER_KEY, listOf(ROOM_KEY, ROOM_KEY), "GET")
            .collectList().block()!!

        assertThat(auth.index.asked).containsExactly(ROOM_KEY, TOPIC_ROOT, ROOM_KEY, TOPIC_ROOT)
    }

    /**
     * **An empty many target list reads no target.** It answers nothing, and it
     * does not read the store.
     */
    @Test
    fun `an empty many target list reads no target`() {
        val auth = recordingAuth(shippedGrants())

        val rows = auth.service.getAuthorizationsAgainstMany(CALLER_KEY, listOf(), "GET")
            .collectList().block()!!

        assertThat(auth.index.asked).isEmpty()
        assertThat(rows).isEmpty()
    }

    /**
     * **An administrator acts on a closed target.**
     *
     * The close is an expired wildcard row on the `User` root, which is a
     * domain root principal. The administrator row is a live wildcard on the
     * `Admin` key, which is an object principal. Level 1 places both at the
     * wildcard, and level 2 places `ENTITY` above `DOMAIN_ROOT`. So the
     * administrator row is last and it decides.
     *
     * **The context must carry the `Admin` key.** The actor set is the `Anon`
     * key, the `User` root and the caller. An anonymous caller does not hold
     * the `Admin` key, so it must fail.
     */
    @Test
    fun `an administrator acts on a closed target`() {
        val rows = listOf(
            grant(ADMIN_KEY, TOPIC_ROOT, "*"),
            grant(USER_ROOT, ROOM_KEY, "*", expires = 1L)
        )
        val service = SpringSecurityAccessBrokerService(broker(rows), rootKeys(), registry())

        val admin = service.hasAccessTo(ROOM_KEY, "GET")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(adminContext())))
            .block()
        val anonymous = service.hasAccessTo(ROOM_KEY, "GET")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(anonymousContext())))
            .block()

        assertThat(admin).describedAs("the administrator").isTrue()
        assertThat(anonymous).describedAs("an anonymous caller").isFalse()
    }

    /**
     * **A close still beats a domain root grant.** Both rows name the
     * `MessageTopic` root as their target. The close is a wildcard, so level 1
     * places it last, and its expiry decides.
     */
    @Test
    fun `a close beats a live named row on a domain root`() {
        val rows = listOf(
            grant(USER_ROOT, TOPIC_ROOT, "GET"),
            grant(USER_ROOT, TOPIC_ROOT, "*", expires = 1L)
        )
        val service = SpringSecurityAccessBrokerService(broker(rows), rootKeys(), registry())

        val answer = service.hasAccessTo(ROOM_KEY, "GET")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(authenticatedContext())))
            .block() ?: false

        assertThat(answer).describedAs("a room read after a close").isFalse()
    }

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

    /**
     * **A close denies a caller who holds no row.** The close wins its group,
     * and its expiry then drops it.
     */
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

    /** The delete check, which reads one room key with `REM`. */
    private fun deleteAnswer(rows: List<AuthMetadata<Long>>, context: SecurityContext): Boolean =
        SpringSecurityAccessBrokerService(broker(rows), rootKeys(), registry())
            .hasAccessTo(ROOM_KEY, "REM")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
            .block() ?: false

    private fun permitted(broker: AuthMetadataAccessBroker<Long>, targets: List<Key<Long>>, perm: String) =
        broker.permittedTargets(CALLER_KEY, targets.map { it.verified() }, perm).collectList().block()!!

    private fun matrixFor(context: SecurityContext?): Map<String, Boolean> =
        operations().associate { (operation, call) -> operation to allowed(call, context) }

    /** Answers `User FIND` against one grant set, which the expiry tests use. */
    private fun allowedWith(grants: List<AuthMetadata<Long>>, context: SecurityContext?): Boolean {
        val service = SpringSecurityAccessBrokerService(broker(grants), rootKeys(), registry())

        return service.hasAccessToDomain("User", "FIND")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context!!)))
            .block() ?: false
    }

    /** One entry per `@PreAuthorize` expression this matrix covers. */
    private fun operations(): List<Pair<String, (SpringSecurityAccessBrokerService<Long>) -> Mono<Boolean>>> =
        listOf(
            "addRoom MessageTopic NEW" to { s -> s.hasAccessToDomain("MessageTopic", "NEW") },
            "send room SEND" to { s -> s.hasAccessTo(ROOM_KEY, "SEND") },
            "whoami User FIND" to { s -> s.hasAccessToDomain("User", "FIND") },
            "messageById GET" to { s -> s.hasAccessTo(MESSAGE_KEY, "GET") },
            "listRooms MessageTopic ALL" to { s -> s.hasAccessToDomain("MessageTopic", "ALL") },
            "addUser User NEW" to { s -> s.hasAccessToDomain("User", "NEW") },
            "deleteRoom MessageTopic REM" to { s -> s.hasAccessTo(ROOM_KEY, "REM") }
        )

    private fun allowed(
        call: (SpringSecurityAccessBrokerService<Long>) -> Mono<Boolean>,
        context: SecurityContext?
    ): Boolean {
        val service = SpringSecurityAccessBrokerService(broker(shippedGrants()), rootKeys(), registry())
        var answer = call(service)
        if (context != null) {
            answer = answer.contextWrite(
                ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context))
            )
        }
        return answer.block() ?: false
    }

    /** The roles of `userinit.yml`, in the order that file lists them. */
    private fun shippedGrants(): List<AuthMetadata<Long>> = listOf(
        grant(ADMIN_KEY, ADMIN_KEY, "*"),
        grant(ANON_KEY, USER_ROOT, "FIND"),
        grant(ANON_KEY, USER_ROOT, "PUT"),
        grant(ANON_KEY, MESSAGE_ROOT, "GET"),
        grant(USER_ROOT, MESSAGE_ROOT, "SEND"),
        grant(USER_ROOT, TOPIC_ROOT, "ALL"),
        grant(USER_ROOT, TOPIC_ROOT, "GET"),
        grant(USER_ROOT, TOPIC_ROOT, "JOIN"),
        grant(USER_ROOT, TOPIC_ROOT, "MEMBERS")
    )

    private fun grant(
        principal: Key<Long>, target: Key<Long>, permission: String, expires: Long = 0L
    ) = StringRoleAuthorizationMetadata(
        TestKeys.key(nextKey.incrementAndGet()), principal, target, permission, false, expires
    )

    private fun broker(grants: List<AuthMetadata<Long>>): AuthMetadataAccessBroker<Long> {
        val store = MapAuthStore()
        val index = MapAuthIndex(store)
        grants.forEach { store.rows[it.key] = it }

        return AuthMetadataAccessBroker(
            CoreAuthorizationService(
                store, index, { it }, { it }, { ANON_KEY }, { USER_ROOT },
                AuthSummarizer({ a, b -> (a.key.id - b.key.id).toInt() }, PrincipalRank(rootKeys())),
                registry(),
            ),
            TestVerifiers.resolvingNothing(),
        )
    }

    /** The same stack as [broker], with an index that records each target. */
    private fun recordingAuth(grants: List<AuthMetadata<Long>>): RecordingAuth {
        val store = MapAuthStore()
        val index = RecordingAuthIndex(store)
        grants.forEach { store.rows[it.key] = it }
        val service = CoreAuthorizationService(
            store, index, { it }, { it }, { ANON_KEY }, { USER_ROOT },
            AuthSummarizer({ a, b -> (a.key.id - b.key.id).toInt() }, PrincipalRank(rootKeys())),
            registry(),
        )
        return RecordingAuth(
            AuthMetadataAccessBroker(service, TestVerifiers.resolvingNothing()), service, index
        )
    }

    /** The registry of every key of this test, each under its own root. */
    private fun registry() = TestVerifiers.holding(
        rootKeys(),
        listOf(ANON_KEY, ADMIN_KEY, USER_ROOT, MESSAGE_ROOT, TOPIC_ROOT, CALLER_KEY, ROOM_KEY, MESSAGE_KEY),
    )

    private fun rootKeys(): RootKeys<Long> = RootKeysFixture.ofLong(
        mapOf(
            ChatDomain.USER to USER_ROOT,
            ChatDomain.MESSAGE to MESSAGE_ROOT,
            ChatDomain.MESSAGE_TOPIC to TOPIC_ROOT
        ),
        admin = ADMIN_KEY,
        anon = ANON_KEY
    )

    private fun anonymousContext() = SecurityContextImpl(
        AnonymousAuthenticationToken(
            "key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        )
    )

    private fun authenticatedContext() = SecurityContextImpl(
        UsernamePasswordAuthenticationToken(chatUserDetails(), "secret", listOf())
    )

    /** A context whose caller is the `Admin` key. */
    private fun adminContext() = SecurityContextImpl(
        UsernamePasswordAuthenticationToken(adminDetails(), "secret", listOf())
    )

    private fun adminDetails() =
        ChatUserDetails(User.create(ADMIN_KEY, "a", "admin", "http://a"), listOf())

    private fun unauthenticatedContext() = SecurityContextImpl(
        UsernamePasswordAuthenticationToken.unauthenticated(chatUserDetails(), "secret")
    )

    private fun unsupportedContext() = SecurityContextImpl(
        UsernamePasswordAuthenticationToken("a-plain-string", "secret", listOf())
    )

    private fun chatUserDetails() =
        ChatUserDetails(User.create(CALLER_KEY, "u", "handle", "http://u"), listOf())

    /** The authorization store, as a map. */
    private class MapAuthStore : PersistenceStore<Long, AuthMetadata<Long>> {
        val rows: MutableMap<Key<Long>, AuthMetadata<Long>> = linkedMapOf()

        override fun key(): Mono<out Key<Long>> = Mono.just(TestKeys.key(nextKey.incrementAndGet()))
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
    private open class MapAuthIndex(private val store: MapAuthStore) :
        IndexService<Long, AuthMetadata<Long>, Key<Long>> {

        override fun add(entity: AuthMetadata<Long>): Mono<Void> = Mono.empty()
        override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
        override fun findBy(query: Key<Long>): Flux<out Key<Long>> =
            Flux.fromIterable(store.rows.values.filter { it.target == query }.map { it.key })

        override fun findUnique(query: Key<Long>): Mono<out Key<Long>> = findBy(query).next()
    }

    /** An index that records every target it is asked for, in order. */
    private class RecordingAuthIndex(store: MapAuthStore) : MapAuthIndex(store) {
        val asked: MutableList<Key<Long>> = mutableListOf()

        override fun findBy(query: Key<Long>): Flux<out Key<Long>> {
            asked.add(query)
            return super.findBy(query)
        }
    }

    /** The broker, the service and the recording index of one grant set. */
    private class RecordingAuth(
        val broker: AuthMetadataAccessBroker<Long>,
        val service: CoreAuthorizationService<Long, Key<Long>>,
        val index: RecordingAuthIndex,
    )

    private companion object {
        val nextKey = AtomicLong(100L)

        /** The `User` domain root. A root key is its own root. */
        val USER_ROOT: Key<Long> = Key.root(3L)

        /** The `Message` domain root. */
        val MESSAGE_ROOT: Key<Long> = Key.root(4L)

        /** The `MessageTopic` domain root. */
        val TOPIC_ROOT: Key<Long> = Key.root(5L)

        /** The `Admin` identity. An identity is an object of the `User` domain. */
        val ADMIN_KEY: Key<Long> = Key.of(2L, 3L)

        /** The `Anon` identity. It is an object of the `User` domain too. */
        val ANON_KEY: Key<Long> = Key.of(1L, 3L)

        /** An ordinary user, and the caller of most contexts of this test. */
        val CALLER_KEY: Key<Long> = Key.of(6L, 3L)

        /** A room. Its root is the `MessageTopic` root. */
        val ROOM_KEY: Key<Long> = Key.of(7L, 5L)

        /** A message. Its root is the `Message` root. */
        val MESSAGE_KEY: Key<Long> = Key.of(8L, 4L)
    }
}
