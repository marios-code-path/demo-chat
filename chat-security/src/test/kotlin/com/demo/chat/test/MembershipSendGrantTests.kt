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
import com.demo.chat.security.service.MembershipSendGrant
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
 * The member `SEND` writer.
 *
 * **This wires the real authorization stack.** `CoreAuthorizationService`,
 * `AuthSummarizer` and `AuthMetadataAccessBroker` are the production classes.
 * Only the store and the index are maps. So each `SEND` answer below is the
 * answer that a deployed check reads. See `CHAT-mfveaecc`.
 *
 * The clock is fixed in the past, so a leave writes an expiry that the
 * summarizer reads as expired.
 */
class MembershipSendGrantTests {

    private val store = MapAuthStore()
    private val service = CoreAuthorizationService(
        store, MapAuthIndex(store), { it }, { it }, { ANON }, { USER_ROOT },
        AuthSummarizer({ a, b -> a.key.id.compareTo(b.key.id) }, PrincipalRank(rootKeys())),
        TestVerifiers.holding(rootKeys(), listOf(ANON, MEMBER, OTHER, ROOM, OTHER_ROOM)),
    )
    private val broker = AuthMetadataAccessBroker(service, TestVerifiers.resolvingNothing())
    private val writer = MembershipSendGrant(
        service, rootKeys(), LongUtil(), Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
    )

    @Test
    fun `a join writes one SEND row that never expires`() {
        assertThat(canSend(MEMBER, ROOM)).describedAs("before the join").isFalse()

        writer.grantSend(MEMBER, ROOM).block()

        val row = store.rows.values.single()
        assertThat(row.principal).describedAs("the member").isEqualTo(MEMBER)
        assertThat(row.target).describedAs("the room").isEqualTo(ROOM)
        assertThat(row.permission).describedAs("the permission").isEqualTo("SEND")
        assertThat(row.expires).describedAs("the expiry").isEqualTo(0L)
        assertThat(canSend(MEMBER, ROOM)).describedAs("after the join").isTrue()
    }

    /** **The leave changes the same row.** It keeps the key and writes the expiry. */
    @Test
    fun `a leave sets the SEND row to expire now`() {
        writer.grantSend(MEMBER, ROOM).block()
        val joined = store.rows.values.single()

        writer.expireSend(MEMBER, ROOM).block()

        val left = store.rows.values.single()
        assertThat(left.key).describedAs("the grant key").isEqualTo(joined.key)
        assertThat(left.expires).describedAs("the expiry").isEqualTo(NOW)
        assertThat(canSend(MEMBER, ROOM)).describedAs("after the leave").isFalse()
    }

    /**
     * **A join after a leave reuses the row.** One expiry per pair means the
     * key order cannot change the answer. A uuid key carries no order.
     */
    @Test
    fun `a join after a leave sets the same row to never expire`() {
        writer.grantSend(MEMBER, ROOM).block()
        writer.expireSend(MEMBER, ROOM).block()

        writer.grantSend(MEMBER, ROOM).block()

        assertThat(store.rows.values.single().expires).isEqualTo(0L)
        assertThat(canSend(MEMBER, ROOM)).isTrue()
    }

    @Test
    fun `a second join writes no second row`() {
        writer.grantSend(MEMBER, ROOM).block()
        writer.grantSend(MEMBER, ROOM).block()

        assertThat(store.rows).hasSize(1)
    }

    @Test
    fun `a leave with no row writes nothing`() {
        writer.expireSend(MEMBER, ROOM).block()

        assertThat(store.rows).isEmpty()
    }

    /** **Both keys are in the actor set of every query.** A row would reach every caller. */
    @Test
    fun `the Anon key and the User root receive no row`() {
        writer.grantSend(ANON, ROOM).block()
        writer.grantSend(USER_ROOT, ROOM).block()
        writer.expireSend(ANON, ROOM).block()

        assertThat(store.rows).isEmpty()
        assertThat(canSend(OTHER, ROOM)).describedAs("a third caller").isFalse()
    }

    /** **The leave reads one member and one room.** Other rows keep their expiry. */
    @Test
    fun `a leave does not touch another member or another room`() {
        writer.grantSend(MEMBER, ROOM).block()
        writer.grantSend(OTHER, ROOM).block()
        writer.grantSend(MEMBER, OTHER_ROOM).block()

        writer.expireSend(MEMBER, ROOM).block()

        assertThat(canSend(MEMBER, ROOM)).describedAs("the member in the room").isFalse()
        assertThat(canSend(OTHER, ROOM)).describedAs("another member").isTrue()
        assertThat(canSend(MEMBER, OTHER_ROOM)).describedAs("another room").isTrue()
    }

    /**
     * **An owner who leaves can still send.** The writer reads `SEND` rows
     * alone. Level 1 of the rank keeps the owner `*` row above them.
     */
    @Test
    fun `a leave does not touch the owner row`() {
        val owner = StringRoleAuthorizationMetadata(
            TestKeys.key(nextKey.incrementAndGet()), MEMBER, ROOM, AuthSummarizer.WILDCARD, false, 0L
        )
        store.rows[owner.key] = owner
        writer.grantSend(MEMBER, ROOM).block()

        writer.expireSend(MEMBER, ROOM).block()

        assertThat(store.rows[owner.key]!!.expires).describedAs("the owner row").isEqualTo(0L)
        assertThat(canSend(MEMBER, ROOM)).describedAs("the owner").isTrue()
    }

    private fun canSend(member: Key<Long>, room: Key<Long>): Boolean =
        broker.hasAccessByKey(member, room.verified(), "SEND").block() ?: false

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
