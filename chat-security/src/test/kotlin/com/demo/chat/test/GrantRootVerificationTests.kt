package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.security.AuthSummarizer
import com.demo.chat.security.rank.PrincipalRank
import com.demo.chat.security.service.CoreAuthorizationService
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * A grant write verifies its principal and its target. See `CHAT-avduuqwp`, T6.
 */
class GrantRootVerificationTests {
    private val roots = FakeKeyServices.longRoots()
    private val keys = FakeKeyServices.long(roots)

    private val principal = keys.register(4801L, ChatDomain.USER)
    private val target = keys.register(4802L, ChatDomain.MESSAGE_TOPIC)

    /** The grant store, as a map. It records each write. */
    private class MapStore(private val roots: com.demo.chat.domain.knownkey.RootKeys<Long>) :
        PersistenceStore<Long, AuthMetadata<Long>> {
        val rows = linkedMapOf<Key<Long>, AuthMetadata<Long>>()
        private var next = 4900L

        override fun key(): Mono<out Key<Long>> = Mono.fromSupplier { Key.of(next++, roots.of(ChatDomain.AUTH_METADATA).id) }
        override fun add(ent: AuthMetadata<Long>): Mono<Void> = Mono.fromRunnable { rows[ent.key] = ent }
        override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { rows.remove(key) }
        override fun get(key: Key<Long>): Mono<out AuthMetadata<Long>> = Mono.justOrEmpty(rows[key])
        override fun all(): Flux<out AuthMetadata<Long>> = Flux.fromIterable(rows.values.toList())
    }

    /** The grant index. It answers every stored grant key for a query by target. */
    private class MapIndex(private val store: MapStore) : IndexService<Long, AuthMetadata<Long>, Key<Long>> {
        override fun add(entity: AuthMetadata<Long>): Mono<Void> = Mono.empty()
        override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
        override fun findBy(query: Key<Long>): Flux<out Key<Long>> =
            Flux.defer { Flux.fromIterable(store.rows.values.filter { it.target == query || it.principal == query }.map { it.key }) }
        override fun findUnique(query: Key<Long>): Mono<out Key<Long>> = findBy(query).next()
    }

    private val store = MapStore(roots)

    private val service = CoreAuthorizationService(
        store, MapIndex(store), { it }, { it }, { roots.anon() }, { roots.of(ChatDomain.USER) },
        AuthSummarizer({ a, b -> (a.key.id - b.key.id).toInt() }, PrincipalRank(roots)),
        KeyVerifier(keys, roots),
    )

    private fun grant(principal: Key<Long>, target: Key<Long>) =
        AuthMetadata.create(Key.empty(0L, roots.of(ChatDomain.AUTH_METADATA).id), principal, target, "GET", Long.MAX_VALUE)

    @Test
    fun `authorize refuses a grant with a forged target root`() {
        StepVerifier.create(service.authorize(grant(principal, Key.of(target.id, roots.of(ChatDomain.USER).id)), true))
            .verifyError(KeyVerificationException::class.java)
        assertThat(store.rows).isEmpty()
    }

    @Test
    fun `authorize refuses a grant with a forged principal root`() {
        StepVerifier.create(service.authorize(grant(Key.of(principal.id, roots.of(ChatDomain.MESSAGE).id), target), true))
            .verifyError(KeyVerificationException::class.java)
        assertThat(store.rows).isEmpty()
    }

    @Test
    fun `authorize refuses a grant with an unknown principal`() {
        StepVerifier.create(service.authorize(grant(Key.of(424280L, roots.of(ChatDomain.USER).id), target), true))
            .verifyError(KeyVerificationException::class.java)
        assertThat(store.rows).isEmpty()
    }

    @Test
    fun `a grant on a domain root is accepted, because a root resolves to itself`() {
        service.authorize(grant(roots.of(ChatDomain.USER), roots.of(ChatDomain.MESSAGE_TOPIC)), true).block()

        assertThat(store.rows).hasSize(1)
    }

    @Test
    fun `reading a stored grant returns both roots`() {
        service.authorize(grant(principal, target), true).block()

        val read = service.getAuthorizationsAgainst(principal, target, "GET").blockFirst()!!
        assertThat(read.principal.root).isEqualTo(principal.root)
        assertThat(read.target.root).isEqualTo(target.root)
    }
}
