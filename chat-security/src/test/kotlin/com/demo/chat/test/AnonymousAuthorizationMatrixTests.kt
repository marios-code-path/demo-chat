package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
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
     * **An anonymous caller may read a user, and nothing else.**
     *
     * `userinit.yml` grants the `Anon` key `User:FIND`, `User:PUT` and
     * `Message:GET`. Only the two `User` grants reach an operation. The
     * `Message:GET` grant names the Message root key, and `messageById`
     * checks a single message key, so that grant never applies.
     */
    @Test
    fun `an anonymous caller may find a user and nothing else`() {
        assertThat(matrixFor(anonymousContext())).isEqualTo(
            mapOf(
                "addRoom MessageTopic NEW" to false,
                "send room SEND" to false,
                "whoami User FIND" to true,
                "messageById GET" to false,
                "listRooms MessageTopic ALL" to true,
                "addUser User NEW" to false
            )
        )
    }

    /**
     * **An authenticated caller reaches the same answers as an anonymous
     * one.** `CoreAuthorizationService` puts the `Anon` key in the actor set
     * of every query, so an anonymous grant is a floor for every caller.
     *
     * The `user: User` rows of `userinit.yml` name the `User` root key as the
     * principal. No caller holds that key, so those four rows reach nobody.
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
        val service = SpringSecurityAccessBrokerService(broker(listOf(row)), rootKeys())

        val answer = service.hasAccessToDomain("MessageTopic", "ALL")
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(authenticatedContext())))
            .block() ?: false

        assertThat(answer).isTrue()
    }

    /**
     * The many target path, which is `getAuthorizationsAgainstMany`. It builds
     * its own actor set, so it needs its own reading.
     */
    @Test
    fun `a row naming the User root reaches a caller through many targets`() {
        val row = grant(USER_ROOT, TOPIC_ROOT, "ALL")

        val answer = broker(listOf(row))
            .hasAccessByManyKeys(CALLER_KEY, listOf(TOPIC_ROOT), "ALL")
            .block() ?: false

        assertThat(answer).isTrue()
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

        assertThat(broker.hasAccessByKey(CALLER_KEY, ADMIN_KEY, "GET").block()).isFalse()
        assertThat(broker.hasAccessByManyKeys(CALLER_KEY, listOf(ADMIN_KEY), "GET").block()).isFalse()
    }

    /**
     * **A key holds every right over itself, and no row is needed.** The rule is
     * in the broker, so the administrator reaches `Admin` with no grant at all.
     */
    @Test
    fun `the administrator holds every right over itself with no row`() {
        val broker = broker(listOf())

        assertThat(broker.hasAccessByKey(ADMIN_KEY, ADMIN_KEY, "GET").block()).isTrue()
    }

    /** The same rule reaches the caller of the security context. */
    @Test
    fun `an authenticated caller holds every right over itself`() {
        val service = SpringSecurityAccessBrokerService(broker(listOf()), rootKeys())

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

        assertThat(broker(listOf(close)).hasAccessByKey(CALLER_KEY, CALLER_KEY, "GET").block()).isTrue()
    }

    /**
     * **Self authority covers the caller alone in a list.** The many target
     * check still reads the grants of every other target. A list that holds
     * only the caller allows. A list that adds a target with no grant denies.
     */
    @Test
    fun `self authority does not widen a many target check`() {
        val broker = broker(listOf())

        assertThat(broker.hasAccessByManyKeys(CALLER_KEY, listOf(CALLER_KEY), "GET").block()).isTrue()
        assertThat(broker.hasAccessByManyKeys(CALLER_KEY, listOf(CALLER_KEY, ROOM_KEY), "GET").block()).isFalse()
    }

    private fun matrixFor(context: SecurityContext?): Map<String, Boolean> =
        operations().associate { (operation, call) -> operation to allowed(call, context) }

    /** Answers `User FIND` against one grant set, which the expiry tests use. */
    private fun allowedWith(grants: List<AuthMetadata<Long>>, context: SecurityContext?): Boolean {
        val service = SpringSecurityAccessBrokerService(broker(grants), rootKeys())

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
            "addUser User NEW" to { s -> s.hasAccessToDomain("User", "NEW") }
        )

    private fun allowed(
        call: (SpringSecurityAccessBrokerService<Long>) -> Mono<Boolean>,
        context: SecurityContext?
    ): Boolean {
        val service = SpringSecurityAccessBrokerService(broker(shippedGrants()), rootKeys())
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
        Key.funKey(nextKey.incrementAndGet()), principal, target, permission, false, expires
    )

    private fun broker(grants: List<AuthMetadata<Long>>): AuthMetadataAccessBroker<Long> {
        val store = MapAuthStore()
        val index = MapAuthIndex(store)
        grants.forEach { store.rows[it.key] = it }

        return AuthMetadataAccessBroker(
            CoreAuthorizationService(
                store, index, { it }, { it }, { ANON_KEY }, { USER_ROOT },
                AuthSummarizer({ a, b -> (a.key.id - b.key.id).toInt() }, PrincipalRank(rootKeys()))
            )
        )
    }

    private fun rootKeys(): RootKeys<Long> = RootKeys<Long>().apply {
        merge(
            mapOf(
                Anon::class.java.simpleName to ANON_KEY,
                Admin::class.java.simpleName to ADMIN_KEY,
                User::class.java.simpleName to USER_ROOT,
                Message::class.java.simpleName to MESSAGE_ROOT,
                MessageTopic::class.java.simpleName to TOPIC_ROOT
            )
        )
    }

    private fun anonymousContext() = SecurityContextImpl(
        AnonymousAuthenticationToken(
            "key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        )
    )

    private fun authenticatedContext() = SecurityContextImpl(
        UsernamePasswordAuthenticationToken(chatUserDetails(), "secret", listOf())
    )

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

        override fun key(): Mono<out Key<Long>> = Mono.just(Key.funKey(nextKey.incrementAndGet()))
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
        val nextKey = AtomicLong(100L)
        val ANON_KEY: Key<Long> = Key.funKey(1L)
        val ADMIN_KEY: Key<Long> = Key.funKey(2L)
        val USER_ROOT: Key<Long> = Key.funKey(3L)
        val MESSAGE_ROOT: Key<Long> = Key.funKey(4L)
        val TOPIC_ROOT: Key<Long> = Key.funKey(5L)
        val CALLER_KEY: Key<Long> = Key.funKey(6L)
        val ROOM_KEY: Key<Long> = Key.funKey(7L)
        val MESSAGE_KEY: Key<Long> = Key.funKey(8L)
    }
}
