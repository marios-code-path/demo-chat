package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.StringRoleAuthorizationMetadata
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.AuthSummarizer
import com.demo.chat.security.access.AuthMetadataAccessBroker
import com.demo.chat.security.rank.PrincipalRank
import com.demo.chat.security.service.CoreAuthorizationService
import com.demo.chat.security.service.MembershipGrant
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.test.key.TestKeys
import com.demo.chat.test.key.TestVerifiers
import com.demo.chat.test.key.verified
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong

/**
 * The membership writer: `SEND` and `SUBSCRIBE`.
 *
 * **This wires the real authorization stack.** `CoreAuthorizationService`,
 * `AuthSummarizer` and `AuthMetadataAccessBroker` are the production classes.
 * Only the store and the index are maps. So each answer below is the answer
 * that a deployed check reads. See `CHAT-mfveaecc` and `CHAT-lfaajjcj`.
 *
 * The clock is fixed in the past, so a leave writes an expiry that the
 * summarizer reads as expired.
 */
class MembershipGrantTests {

    private val store = MapAuthStore()
    private val service = CoreAuthorizationService(
        store, MapAuthIndex(store), { it }, { it }, { ANON }, { USER_ROOT },
        AuthSummarizer({ a, b -> a.key.id.compareTo(b.key.id) }, PrincipalRank(rootKeys())),
        TestVerifiers.holding(rootKeys(), listOf(ANON, MEMBER, OTHER, ROOM, OTHER_ROOM)),
    )
    private val broker = AuthMetadataAccessBroker(service, TestVerifiers.resolvingNothing())
    private val writer = MembershipGrant(
        service, rootKeys(), LongUtil(), Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
    )

    @Test
    fun `a join writes one SEND row and one SUBSCRIBE row that never expire`() {
        assertThat(can(MEMBER, ROOM, "SEND")).describedAs("SEND before the join").isFalse()
        assertThat(can(MEMBER, ROOM, "SUBSCRIBE")).describedAs("SUBSCRIBE before the join").isFalse()

        writer.grantMembership(MEMBER, ROOM).block()

        assertThat(store.rows.values.map { it.permission }).containsExactlyInAnyOrder("SEND", "SUBSCRIBE")
        assertThat(store.rows.values).allSatisfy { row ->
            assertThat(row.principal).describedAs("the member").isEqualTo(MEMBER)
            assertThat(row.target).describedAs("the room").isEqualTo(ROOM)
            assertThat(row.expires).describedAs("the expiry").isEqualTo(0L)
        }
        assertThat(can(MEMBER, ROOM, "SEND")).describedAs("SEND after the join").isTrue()
        assertThat(can(MEMBER, ROOM, "SUBSCRIBE")).describedAs("SUBSCRIBE after the join").isTrue()
    }

    /** **The leave changes the same rows.** It keeps the keys and writes the expiry. */
    @Test
    fun `a leave sets both rows to expire now`() {
        writer.grantMembership(MEMBER, ROOM).block()
        val joined = store.rows.keys.toSet()

        writer.expireMembership(MEMBER, ROOM).block()

        assertThat(store.rows.keys).describedAs("the grant keys").isEqualTo(joined)
        assertThat(store.rows.values.map { it.expires }).describedAs("the expiries").containsOnly(NOW)
        assertThat(can(MEMBER, ROOM, "SEND")).describedAs("SEND after the leave").isFalse()
        assertThat(can(MEMBER, ROOM, "SUBSCRIBE")).describedAs("SUBSCRIBE after the leave").isFalse()
    }

    /**
     * **A join after a leave reuses the rows.** One expiry per member, room and
     * permission means the key order cannot change the answer. A uuid key
     * carries no order.
     */
    @Test
    fun `a join after a leave sets the same rows to never expire`() {
        writer.grantMembership(MEMBER, ROOM).block()
        writer.expireMembership(MEMBER, ROOM).block()

        writer.grantMembership(MEMBER, ROOM).block()

        assertThat(store.rows).hasSize(2)
        assertThat(store.rows.values.map { it.expires }).containsOnly(0L)
        assertThat(can(MEMBER, ROOM, "SEND")).isTrue()
        assertThat(can(MEMBER, ROOM, "SUBSCRIBE")).isTrue()
    }

    @Test
    fun `a second join writes no further row`() {
        writer.grantMembership(MEMBER, ROOM).block()
        writer.grantMembership(MEMBER, ROOM).block()

        assertThat(store.rows).hasSize(2)
    }

    /** **A row that one permission lost is written again**, and the other row is kept. */
    @Test
    fun `a join writes a missing SUBSCRIBE row beside an existing SEND row`() {
        val send = StringRoleAuthorizationMetadata(
            TestKeys.key(nextKey.incrementAndGet()), MEMBER, ROOM, "SEND", false, 0L
        )
        store.rows[send.key] = send

        writer.grantMembership(MEMBER, ROOM).block()

        assertThat(store.rows.values.map { it.permission }).containsExactlyInAnyOrder("SEND", "SUBSCRIBE")
        assertThat(store.rows).containsKey(send.key)
    }

    @Test
    fun `a leave with no row writes nothing`() {
        writer.expireMembership(MEMBER, ROOM).block()

        assertThat(store.rows).isEmpty()
    }

    /** **Both keys are in the actor set of every query.** A row would reach every caller. */
    @Test
    fun `the Anon key and the User root receive no row`() {
        writer.grantMembership(ANON, ROOM).block()
        writer.grantMembership(USER_ROOT, ROOM).block()
        writer.expireMembership(ANON, ROOM).block()

        assertThat(store.rows).isEmpty()
        assertThat(can(OTHER, ROOM, "SEND")).describedAs("SEND for a third caller").isFalse()
        assertThat(can(OTHER, ROOM, "SUBSCRIBE")).describedAs("SUBSCRIBE for a third caller").isFalse()
    }

    /** **The leave reads one member and one room.** Other rows keep their expiry. */
    @Test
    fun `a leave does not touch another member or another room`() {
        writer.grantMembership(MEMBER, ROOM).block()
        writer.grantMembership(OTHER, ROOM).block()
        writer.grantMembership(MEMBER, OTHER_ROOM).block()

        writer.expireMembership(MEMBER, ROOM).block()

        for (permission in listOf("SEND", "SUBSCRIBE")) {
            assertThat(can(MEMBER, ROOM, permission)).describedAs("$permission, the member in the room").isFalse()
            assertThat(can(OTHER, ROOM, permission)).describedAs("$permission, another member").isTrue()
            assertThat(can(MEMBER, OTHER_ROOM, permission)).describedAs("$permission, another room").isTrue()
        }
    }

    /**
     * **An owner who leaves can still send and listen.** The writer reads `SEND`
     * and `SUBSCRIBE` rows alone. Level 1 of the rank keeps the owner `*` row
     * above them.
     */
    @Test
    fun `a leave does not touch the owner row`() {
        val owner = StringRoleAuthorizationMetadata(
            TestKeys.key(nextKey.incrementAndGet()), MEMBER, ROOM, AuthSummarizer.WILDCARD, false, 0L
        )
        store.rows[owner.key] = owner
        writer.grantMembership(MEMBER, ROOM).block()

        writer.expireMembership(MEMBER, ROOM).block()

        assertThat(store.rows[owner.key]!!.expires).describedAs("the owner row").isEqualTo(0L)
        assertThat(can(MEMBER, ROOM, "SEND")).describedAs("the owner sends").isTrue()
        assertThat(can(MEMBER, ROOM, "SUBSCRIBE")).describedAs("the owner listens").isTrue()
    }

    private fun can(member: Key<Long>, room: Key<Long>, permission: String): Boolean =
        broker.hasAccessByKey(member, room.verified(), permission).block() ?: false

    private fun rootKeys(): RootKeys<Long> = RootKeysFixture.ofLong(
        mapOf(
            ChatDomain.USER to USER_ROOT,
            ChatDomain.MESSAGE to MESSAGE_ROOT,
            ChatDomain.MESSAGE_TOPIC to TOPIC_ROOT,
        ),
        admin = ADMIN,
        anon = ANON,
    )

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
        /** The time of every leave in this test. It is in the past. */
        const val NOW = 1_000_000L

        val nextKey = AtomicLong(100L)

        val USER_ROOT: Key<Long> = Key.root(3L)
        val MESSAGE_ROOT: Key<Long> = Key.root(4L)
        val TOPIC_ROOT: Key<Long> = Key.root(5L)

        val ADMIN: Key<Long> = Key.of(2L, 3L)
        val ANON: Key<Long> = Key.of(1L, 3L)
        val MEMBER: Key<Long> = Key.of(6L, 3L)
        val OTHER: Key<Long> = Key.of(8L, 3L)

        val ROOM: Key<Long> = Key.of(7L, 5L)
        val OTHER_ROOM: Key<Long> = Key.of(9L, 5L)
    }
}
