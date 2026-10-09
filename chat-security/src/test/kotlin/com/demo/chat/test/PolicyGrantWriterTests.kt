package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.AuthSummarizer
import com.demo.chat.security.rank.PrincipalRank
import com.demo.chat.security.service.CoreAuthorizationService
import com.demo.chat.security.service.PolicyGrantWriter
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.security.SecondOwnerException
import com.demo.chat.service.security.policy.ActionContext
import com.demo.chat.service.security.policy.ActionTrigger
import com.demo.chat.service.security.policy.GrantExpiry
import com.demo.chat.service.security.policy.GrantPolicy
import com.demo.chat.service.security.policy.GrantRule
import com.demo.chat.service.security.policy.NoGrantPrincipal
import com.demo.chat.service.security.policy.PrincipalSource
import com.demo.chat.service.security.policy.ShippedGrantPolicies
import com.demo.chat.service.security.policy.TargetSource
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.test.key.TestVerifiers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong

/**
 * The writer that maps policy intents to `AuthMetadata` rows. See
 * `CHAT-qojwcatx`.
 *
 * **This wires the real authorization stack.** `CoreAuthorizationService` and
 * `AuthSummarizer` are the production classes. Only the store and the index are
 * maps. The store mints each key with the `AUTH_METADATA` root, as the
 * production key service does.
 */
class PolicyGrantWriterTests {

    private val store = MapAuthStore()
    private val service = CoreAuthorizationService(
        store, MapAuthIndex(store), { it }, { it }, { ANON }, { USER_ROOT },
        AuthSummarizer({ a, b -> a.key.id.compareTo(b.key.id) }, PrincipalRank(rootKeys)),
        TestVerifiers.holding(rootKeys, listOf(ANON, MEMBER, OTHER, ROOM, USER_ROOT)),
    )

    private fun writer(enabled: List<GrantPolicy> = ShippedGrantPolicies.ALL) =
        PolicyGrantWriter(service, rootKeys, LongUtil(), enabled, Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC))

    /** **One policy gives more than one write.** Each row keeps its principal, target, permission and expiry. */
    @Test
    fun `a join policy writes one row per intent`() {
        writer().apply(ShippedGrantPolicies.ROOM_JOIN, ActionContext(MEMBER, ROOM)).block()

        assertThat(rows()).containsExactlyInAnyOrder(
            Row(MEMBER, ROOM, "SEND", 0L),
            Row(MEMBER, ROOM, "SUBSCRIBE", 0L),
        )
    }

    /** **`NOW` reads the clock once.** Both rows carry the same expiry. */
    @Test
    fun `a leave policy sets each live row to expire at the action time`() {
        writer().apply(ShippedGrantPolicies.ROOM_JOIN, ActionContext(MEMBER, ROOM)).block()
        val joined = store.rows.keys.toSet()

        writer().apply(ShippedGrantPolicies.ROOM_LEAVE, ActionContext(MEMBER, ROOM)).block()

        assertThat(store.rows.keys).describedAs("the grant keys").isEqualTo(joined)
        assertThat(rows()).containsExactlyInAnyOrder(
            Row(MEMBER, ROOM, "SEND", NOW),
            Row(MEMBER, ROOM, "SUBSCRIBE", NOW),
        )
    }

    /** **A `NOW` intent with no live row writes nothing.** */
    @Test
    fun `a leave with no stored row writes no row`() {
        writer().apply(ShippedGrantPolicies.ROOM_LEAVE, ActionContext(MEMBER, ROOM)).block()

        assertThat(store.rows).isEmpty()
    }

    @Test
    fun `the owner policy writes the wildcard for the active principal`() {
        writer().apply(ShippedGrantPolicies.ROOM_OWNER, ActionContext(MEMBER, ROOM)).block()
        writer().apply(ShippedGrantPolicies.ROOM_OWNER, ActionContext(MEMBER, ROOM)).block()

        assertThat(rows()).containsExactly(Row(MEMBER, ROOM, "*", 0L))
    }

    /** **The owner guard still runs.** A new `*` row goes through `authorize`. */
    @Test
    fun `a second owner is refused`() {
        writer().apply(ShippedGrantPolicies.ROOM_OWNER, ActionContext(MEMBER, ROOM)).block()

        StepVerifier.create(writer().apply(ShippedGrantPolicies.ROOM_OWNER, ActionContext(OTHER, ROOM)))
            .expectError(SecondOwnerException::class.java)
            .verify()
        assertThat(rows()).containsExactly(Row(MEMBER, ROOM, "*", 0L))
    }

    /**
     * **A grant row key is never the target key.** Each write mints its own key
     * with the `AUTH_METADATA` root. The room key keeps the `MessageTopic` root.
     */
    @Test
    fun `each write mints an independent AUTH_METADATA key`() {
        writer().apply(ShippedGrantPolicies.ROOM_OWNER, ActionContext(MEMBER, ROOM)).block()
        writer().apply(ShippedGrantPolicies.ROOM_JOIN, ActionContext(MEMBER, ROOM)).block()

        val keys = store.rows.values.map { it.key }
        assertThat(keys).hasSize(3).doesNotHaveDuplicates()
        assertThat(keys).allSatisfy { key ->
            assertThat(key.root).describedAs("the grant key root").isEqualTo(AUTH_ROOT.id)
            assertThat(key.empty).describedAs("a minted key").isFalse()
            assertThat(key).describedAs("the grant key").isNotEqualTo(ROOM)
            assertThat(key.id).describedAs("the grant key id").isNotEqualTo(ROOM.id)
        }
        assertThat(store.rows.values.map { it.target }).containsOnly(ROOM)
        assertThat(ROOM.root).isEqualTo(TOPIC_ROOT.id)
    }

    /** **A new policy has no effect until it is enabled.** */
    @Test
    fun `a policy that is not enabled writes nothing`() {
        writer().apply(DRAFT_ADD, ActionContext(MEMBER, ROOM)).block()

        assertThat(store.rows).isEmpty()
    }

    /** **The same policy writes once it is enabled.** `ROOT` resolves to the `User` root. */
    @Test
    fun `an enabled policy writes its intents`() {
        writer(ShippedGrantPolicies.ALL + DRAFT_ADD).apply(DRAFT_ADD, ActionContext(MEMBER, ROOM)).block()

        assertThat(rows()).containsExactly(Row(USER_ROOT, ROOM, "GET", 0L))
    }

    /** **A shipped policy is not enabled by default.** The list is explicit. */
    @Test
    fun `a writer that enables only the owner policy ignores a join`() {
        writer(listOf(ShippedGrantPolicies.ROOM_OWNER)).apply(ShippedGrantPolicies.ROOM_JOIN, ActionContext(MEMBER, ROOM)).block()

        assertThat(store.rows).isEmpty()
    }

    @Test
    fun `two enabled policies with one name are refused`() {
        val copy = GrantPolicy(
            ShippedGrantPolicies.ROOM_JOIN.name, ShippedGrantPolicies.ROOM_JOIN.trigger,
            ShippedGrantPolicies.ROOM_JOIN.rules, ShippedGrantPolicies.ROOM_JOIN.noGrant,
        )
        assertThatThrownBy { writer(ShippedGrantPolicies.ALL + copy) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("share a name")
    }

    @Test
    fun `Anon gets no row from an enabled policy`() {
        ShippedGrantPolicies.ALL.forEach { policy -> writer().apply(policy, ActionContext(ANON, ROOM)).block() }

        assertThat(store.rows).isEmpty()
    }

    private fun rows(): List<Row> = store.rows.values.map { Row(it.principal, it.target, it.permission, it.expires) }

    private data class Row(val principal: Key<Long>, val target: Key<Long>, val permission: String, val expires: Long)

    /** The authorization store, as a map. It mints keys with the `AUTH_METADATA` root. */
    private class MapAuthStore : PersistenceStore<Long, AuthMetadata<Long>> {
        val rows: MutableMap<Key<Long>, AuthMetadata<Long>> = linkedMapOf()

        override fun key(): Mono<out Key<Long>> = Mono.fromSupplier { Key.of(nextKey.incrementAndGet(), AUTH_ROOT.id) }
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
        /** The time of every action in this test. It is in the past. */
        const val NOW = 1_000_000L

        val nextKey = AtomicLong(100L)

        val USER_ROOT: Key<Long> = Key.root(3L)
        val TOPIC_ROOT: Key<Long> = Key.root(5L)
        val AUTH_ROOT: Key<Long> = Key.root(11L)

        val ADMIN: Key<Long> = Key.of(2L, 3L)
        val ANON: Key<Long> = Key.of(1L, 3L)
        val MEMBER: Key<Long> = Key.of(6L, 3L)
        val OTHER: Key<Long> = Key.of(8L, 3L)

        /** A room. Its root is the `MessageTopic` root. */
        val ROOM: Key<Long> = Key.of(7L, 5L)

        /** A policy that no configuration enables. */
        val DRAFT_ADD = GrantPolicy(
            "draftadd", ActionTrigger.ROOM_ADDED,
            listOf(GrantRule(PrincipalSource.ROOT, TargetSource.ROOM, "GET", GrantExpiry.NONE)),
            setOf(NoGrantPrincipal.ANON),
        )

        val rootKeys: RootKeys<Long> = RootKeysFixture.ofLong(
            mapOf(
                ChatDomain.USER to USER_ROOT,
                ChatDomain.MESSAGE_TOPIC to TOPIC_ROOT,
                ChatDomain.AUTH_METADATA to AUTH_ROOT,
            ),
            admin = ADMIN,
            anon = ANON,
        )
    }
}
