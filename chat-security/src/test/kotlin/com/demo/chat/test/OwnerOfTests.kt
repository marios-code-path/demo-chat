package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.StringRoleAuthorizationMetadata
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.AuthSummarizer
import com.demo.chat.security.rank.PrincipalRank
import com.demo.chat.security.service.CoreAuthorizationService
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.security.SecondOwnerException
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.test.key.TestKeys
import com.demo.chat.test.key.TestVerifiers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.concurrent.atomic.AtomicLong

/**
 * The owner read of one target.
 *
 * **The read is a scan of the persistence store.** It never reads the index,
 * so the index of every test here fails on a read. Each case seeds the store
 * alone, and the store holds no index row.
 *
 * An owner row is a live `*` row whose principal is an entity. See
 * `CHAT-esengqpv` and `CHAT-vctcmicv`.
 */
class OwnerOfTests {

    /** One live entity `*` row and one expired domain root `*` row answer the entity. */
    @Test
    fun `a live entity owner answers beside a close row`() {
        val service = service(
            owner(OWNER, TARGET),
            close()
        )

        assertThat(service.ownerOf(TARGET).block()).isEqualTo(OWNER)
    }

    /** One expired domain root `*` row is a close. It names no owner. */
    @Test
    fun `a close row alone answers no owner`() {
        val service = service(close())

        assertThat(service.ownerOf(TARGET).block()).isNull()
    }

    /** A live domain root `*` row is not an owner row. It names no owner. */
    @Test
    fun `a live domain root row alone answers no owner`() {
        val service = service(grant(TOPIC_ROOT, TARGET, "*"))

        assertThat(service.ownerOf(TARGET).block()).isNull()
    }

    /** Two live entity `*` rows break the one-owner rule. */
    @Test
    fun `two live entity rows report a second owner`() {
        val service = service(
            grant(OWNER, TARGET, "*"),
            grant(OTHER_ENTITY, TARGET, "*")
        )

        StepVerifier.create(service.ownerOf(TARGET))
            .expectError(SecondOwnerException::class.java)
            .verify()
    }

    /** An expired entity row of the owner does not count beside the live row. */
    @Test
    fun `an expired entity row of the same principal is ignored`() {
        val service = service(
            grant(OWNER, TARGET, "*"),
            grant(OWNER, TARGET, "*", expired())
        )

        assertThat(service.ownerOf(TARGET).block()).isEqualTo(OWNER)
    }

    /** A named permission is not an owner row, even on the exact target. */
    @Test
    fun `a named permission row answers no owner`() {
        val service = service(grant(OWNER, TARGET, "GET"))

        assertThat(service.ownerOf(TARGET).block()).isNull()
    }

    /** An owner row of another target does not answer. */
    @Test
    fun `an owner row of another target does not answer`() {
        val service = service(grant(OWNER, OTHER_TARGET, "*"))

        assertThat(service.ownerOf(TARGET).block()).isNull()
    }

    /** An empty store answers no owner, and it does not error. */
    @Test
    fun `an empty store answers no owner`() {
        val service = service()

        assertThat(service.ownerOf(TARGET).block()).isNull()
    }

    private fun owner(principal: Key<Long>, target: Key<Long>): AuthMetadata<Long> =
        grant(principal, target, "*")

    private fun grant(
        principal: Key<Long>,
        target: Key<Long>,
        permission: String,
        expires: Long = 0L
    ): AuthMetadata<Long> = StringRoleAuthorizationMetadata(
        TestKeys.key(nextKey.incrementAndGet()), principal, target, permission, false, expires
    )

    private fun close(): AuthMetadata<Long> = grant(TOPIC_ROOT, TARGET, "*", expired())

    private fun expired(): Long = System.currentTimeMillis() - 60_000L

    private fun service(vararg rows: AuthMetadata<Long>): CoreAuthorizationService<Long, Key<Long>> =
        CoreAuthorizationService(
            Store(rows.toList()),
            NoReadIndex(),
            { it },
            { it },
            { ANON_KEY },
            { USER_ROOT },
            AuthSummarizer({ a, b -> (a.key.id - b.key.id).toInt() }, PrincipalRank(rootKeys())),
            TestVerifiers.resolvingNothing(),
        )

    private fun rootKeys(): RootKeys<Long> = RootKeysFixture.ofLong(
        mapOf(
            ChatDomain.USER to USER_ROOT,
            ChatDomain.MESSAGE_TOPIC to TOPIC_ROOT
        ),
        admin = ADMIN_KEY,
        anon = ANON_KEY
    )

    /** The rows of one test. It reports the same rows for `all`. */
    private class Store(private val rows: List<AuthMetadata<Long>>) : PersistenceStore<Long, AuthMetadata<Long>> {
        override fun key(): Mono<out Key<Long>> = Mono.just(TestKeys.key(nextKey.incrementAndGet()))
        override fun add(ent: AuthMetadata<Long>): Mono<Void> = Mono.empty()
        override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
        override fun get(key: Key<Long>): Mono<out AuthMetadata<Long>> =
            Mono.justOrEmpty(rows.firstOrNull { it.key == key })
        override fun all(): Flux<out AuthMetadata<Long>> = Flux.fromIterable(rows)
    }

    /** An index that fails on every read, so a scan cannot hide an index read. */
    private class NoReadIndex : IndexService<Long, AuthMetadata<Long>, Key<Long>> {
        override fun add(entity: AuthMetadata<Long>): Mono<Void> = Mono.empty()
        override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
        override fun findBy(query: Key<Long>): Flux<out Key<Long>> =
            Flux.error(IllegalStateException("The owner read must not read the index."))
        override fun findUnique(query: Key<Long>): Mono<out Key<Long>> = Mono.error(
            IllegalStateException("The owner read must not read the index.")
        )
    }

    private companion object {
        val nextKey = AtomicLong(500L)

        val USER_ROOT: Key<Long> = Key.root(3L)
        val TOPIC_ROOT: Key<Long> = Key.root(5L)
        val ADMIN_KEY: Key<Long> = Key.of(2L, 3L)
        val ANON_KEY: Key<Long> = Key.of(1L, 3L)

        /** An entity principal. It is its own key, not a domain root. */
        val OWNER: Key<Long> = Key.of(6L, 3L)

        /** A second entity principal. */
        val OTHER_ENTITY: Key<Long> = Key.of(7L, 3L)

        /** A room key of the `MessageTopic` domain. */
        val TARGET: Key<Long> = Key.of(10L, 5L)

        /** Another room key of the same domain. */
        val OTHER_TARGET: Key<Long> = Key.of(11L, 5L)
    }
}
