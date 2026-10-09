package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.AuthSummarizer
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.security.rank.PrincipalRank
import com.demo.chat.security.service.ContextRoomOwnerGrant
import com.demo.chat.security.service.CoreAuthorizationService
import com.demo.chat.security.service.MembershipGrant
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.security.policy.ActionContext
import com.demo.chat.service.security.policy.GrantExpiry
import com.demo.chat.service.security.policy.GrantIntent
import com.demo.chat.service.security.policy.GrantPolicy
import com.demo.chat.service.security.policy.ShippedGrantPolicies
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong

/**
 * Each shipped grant policy against the rows of its shipped writer.
 *
 * **The policy is the statement, and the writer is the evidence.** Each test
 * runs the production writer against a map store, and evaluates the policy for
 * the same action. The stored rows must equal the intents. The expiry `NONE`
 * reads as 0, and `NOW` reads as the time of the action. See `CHAT-xdsetfkf`.
 *
 * `CHAT-qojwcatx` moves the writers onto the policies. These tests then hold
 * the behaviour that the move must keep.
 */
class ShippedGrantPolicyTests {

    private val store = MapAuthStore()
    private val service = CoreAuthorizationService(
        store, MapAuthIndex(store), { it }, { it }, { ANON }, { USER_ROOT },
        AuthSummarizer({ a, b -> a.key.id.compareTo(b.key.id) }, PrincipalRank(rootKeys)),
        TestVerifiers.holding(rootKeys, listOf(ANON, MEMBER, ROOM, USER_ROOT)),
    )
    private val owner = ContextRoomOwnerGrant(ContextIdentity(rootKeys), service, rootKeys, LongUtil())
    private val membership = MembershipGrant(service, rootKeys, LongUtil(), Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC))

    @Test
    fun `add, an authenticated caller - the owner row equals the policy`() {
        owner.grantOwner(ROOM).contextWrite(context(authenticated())).block()

        assertRowsEqual(ShippedGrantPolicies.ROOM_OWNER, ActionContext(MEMBER, ROOM), expectedCount = 1)
    }

    @Test
    fun `add, an anonymous caller - no row and no intent`() {
        owner.grantOwner(ROOM).contextWrite(context(anonymous())).block()

        assertRowsEqual(ShippedGrantPolicies.ROOM_OWNER, ActionContext(ANON, ROOM), expectedCount = 0)
    }

    @Test
    fun `add, no identity - no row and no intent`() {
        owner.grantOwner(ROOM).block()

        assertRowsEqual(ShippedGrantPolicies.ROOM_OWNER, ActionContext(null, ROOM), expectedCount = 0)
    }

    @Test
    fun `join - the SEND and SUBSCRIBE rows equal the policy`() {
        membership.grantMembership(MEMBER, ROOM).block()

        assertRowsEqual(ShippedGrantPolicies.ROOM_JOIN, ActionContext(MEMBER, ROOM), expectedCount = 2)
    }

    @Test
    fun `join, Anon or the User root - no row and no intent`() {
        for (principal in listOf(ANON, USER_ROOT)) {
            membership.grantMembership(principal, ROOM).block()

            assertRowsEqual(ShippedGrantPolicies.ROOM_JOIN, ActionContext(principal, ROOM), expectedCount = 0)
        }
    }

    /** **A leave expires the rows of the join.** The rows after the leave equal the leave intents. */
    @Test
    fun `leave after a join - the expired rows equal the policy`() {
        membership.grantMembership(MEMBER, ROOM).block()

        membership.expireMembership(MEMBER, ROOM).block()

        assertRowsEqual(ShippedGrantPolicies.ROOM_LEAVE, ActionContext(MEMBER, ROOM), expectedCount = 2)
    }

    @Test
    fun `leave, Anon or the User root - no row and no intent`() {
        for (principal in listOf(ANON, USER_ROOT)) {
            membership.expireMembership(principal, ROOM).block()

            assertRowsEqual(ShippedGrantPolicies.ROOM_LEAVE, ActionContext(principal, ROOM), expectedCount = 0)
        }
    }

    /** The shipped writers and the model spell the wildcard the same way. */
    @Test
    fun `the model wildcard is the summarizer wildcard`() {
        assertThat(GrantPolicy.WILDCARD).isEqualTo(AuthSummarizer.WILDCARD)
    }

    private fun assertRowsEqual(policy: GrantPolicy, action: ActionContext<Long>, expectedCount: Int) {
        val intents = policy.intents(action, rootKeys)
        val rows = store.rows.values.map { row -> GrantRow(row.principal, row.target, row.permission, row.expires) }

        assertThat(intents).describedAs("the intents of ${policy.name}").hasSize(expectedCount)
        assertThat(rows).describedAs("the rows of the ${policy.name} writer")
            .containsExactlyInAnyOrderElementsOf(intents.map { it.asRow() })
    }

    private fun GrantIntent<Long>.asRow(): GrantRow = GrantRow(
        principal, target, permission,
        when (expiry) {
            GrantExpiry.NONE -> 0L
            GrantExpiry.NOW -> NOW
        },
    )

    private data class GrantRow(val principal: Key<Long>, val target: Key<Long>, val permission: String, val expires: Long)

    private fun authenticated() = SecurityContextImpl(
        UsernamePasswordAuthenticationToken(
            ChatUserDetails(User.create(MEMBER, "u", "handle", "http://u"), listOf()), "secret", listOf()
        )
    )

    /** The token that the RSocket server seam installs for a caller with no credential. */
    private fun anonymous() = SecurityContextImpl(
        AnonymousAuthenticationToken("key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")))
    )

    private fun context(that: SecurityContext) = ReactiveSecurityContextHolder.withSecurityContext(Mono.just(that))

    /** The authorization store, as a map. */
    private class MapAuthStore : PersistenceStore<Long, AuthMetadata<Long>> {
        val rows: MutableMap<Key<Long>, AuthMetadata<Long>> = linkedMapOf()

        override fun key(): Mono<out Key<Long>> = Mono.fromSupplier { TestKeys.key(nextKey.incrementAndGet()) }
        override fun add(ent: AuthMetadata<Long>): Mono<Void> = Mono.fromRunnable { rows[ent.key] = ent }
        override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { rows.remove(key) }
        override fun get(key: Key<Long>): Mono<out AuthMetadata<Long>> = Mono.justOrEmpty(rows[key])
        override fun all(): Flux<out AuthMetadata<Long>> = Flux.fromIterable(rows.values)
    }

    /** The authorization index, which answers by target key. */
    private class MapAuthIndex(private val store: MapAuthStore) :
        IndexService<Long, AuthMetadata<Long>, Key<Long>> {

        override fun add(entity: AuthMetadata<Long>): Mono<Void> = Mono.empty()
        override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
        override fun findBy(query: Key<Long>): Flux<out Key<Long>> =
            Flux.defer { Flux.fromIterable(store.rows.values.filter { it.target == query }.map { it.key }) }

        override fun findUnique(query: Key<Long>): Mono<out Key<Long>> = findBy(query).next()
    }

    private companion object {
        /** The time of every leave in this test. */
        const val NOW = 1_000_000L

        val nextKey = AtomicLong(100L)

        val USER_ROOT: Key<Long> = Key.root(3L)
        val TOPIC_ROOT: Key<Long> = Key.root(5L)

        val ADMIN: Key<Long> = Key.of(2L, 3L)
        val ANON: Key<Long> = Key.of(1L, 3L)
        val MEMBER: Key<Long> = Key.of(6L, 3L)

        val ROOM: Key<Long> = Key.of(7L, 5L)

        val rootKeys: RootKeys<Long> = RootKeysFixture.ofLong(
            mapOf(ChatDomain.USER to USER_ROOT, ChatDomain.MESSAGE_TOPIC to TOPIC_ROOT),
            admin = ADMIN,
            anon = ANON,
        )
    }
}
