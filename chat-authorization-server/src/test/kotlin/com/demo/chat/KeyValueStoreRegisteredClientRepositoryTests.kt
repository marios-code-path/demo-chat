package com.demo.chat

import com.demo.chat.auth.client.KeyValueStoreRegisteredClientRepository
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.service.core.KeyValueIndexService
import com.demo.chat.service.core.KeyValueStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * save() discarded both publishers inside doOnNext. Neither write received a
 * subscription, so a registered client was never stored and never indexed.
 *
 * The fakes record only what a subscription reaches. An unsubscribed
 * publisher records nothing.
 */
class KeyValueStoreRegisteredClientRepositoryTests {

    private val clientId: UUID = UUID.randomUUID()

    private val client: RegisteredClient = RegisteredClient
        .withId(clientId.toString())
        .clientId("a-client")
        .clientName("A Client")
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .build()

    private val kvRoot: UUID = UUID.randomUUID()

    /** Mints [minted] keys under [kvRoot]. With `mints = false` it mints nothing. */
    private inner class RecordingStore(private val mints: Boolean = true) : KeyValueStore<UUID, Any> {
        val added = mutableListOf<KeyValuePair<UUID, Any>>()
        val minted = mutableListOf<Key<UUID>>()

        override fun key(): Mono<out Key<UUID>> =
            if (mints) Mono.fromSupplier { Key.of(UUID.randomUUID(), kvRoot).also { minted.add(it) } } else Mono.empty()
        override fun add(ent: KeyValuePair<UUID, Any>): Mono<Void> =
            Mono.fromRunnable { added.add(ent) }

        override fun rem(key: Key<UUID>): Mono<Void> = Mono.empty()
        override fun get(key: Key<UUID>): Mono<out KeyValuePair<UUID, Any>> = Mono.empty()
        override fun all(): Flux<out KeyValuePair<UUID, Any>> = Flux.empty()
    }

    /** Answers the `id` field from what it indexed, as the production index does. */
    private class RecordingIndex : KeyValueIndexService<UUID, IndexSearchRequest> {
        val added = mutableListOf<KeyValuePair<UUID, Any>>()

        override fun add(entity: KeyValuePair<UUID, Any>): Mono<Void> =
            Mono.fromRunnable { added.add(entity) }

        override fun rem(key: Key<UUID>): Mono<Void> = Mono.empty()
        override fun findBy(query: IndexSearchRequest): Flux<out Key<UUID>> = Flux.defer {
            Flux.fromIterable(
                added.filter { query.first == "id" && (it.data as RegisteredClient).id == query.second }
                    .map { it.key }
                    .distinct()
            )
        }
        override fun findUnique(query: IndexSearchRequest): Mono<out Key<UUID>> = findBy(query).singleOrEmpty()
    }

    @Test
    fun `save subscribes the store write and the index write`() {
        val store = RecordingStore()
        val index = RecordingIndex()

        KeyValueStoreRegisteredClientRepository(index, store, UUIDUtil()).save(client)

        Assertions.assertThat(store.added).hasSize(1)
        Assertions.assertThat(index.added).hasSize(1)
        Assertions.assertThat(index.added.first().data).isEqualTo(client)
    }

    // A client id is text, not a key. The first save stores the client under a
    // minted KEY_VALUE_PAIR key. See CHAT-avduuqwp, E11.
    @Test
    fun `the first save stores the client under a minted key`() {
        val store = RecordingStore()

        KeyValueStoreRegisteredClientRepository(RecordingIndex(), store, UUIDUtil()).save(client)

        Assertions.assertThat(store.added.single().key).isEqualTo(store.minted.single())
        Assertions.assertThat(store.added.single().key.root).isEqualTo(kvRoot)
        Assertions.assertThat(store.added.single().key.id).isNotEqualTo(clientId)
    }

    @Test
    fun `a later save replaces the entry under the key of the first save`() {
        val store = RecordingStore()
        val repository = KeyValueStoreRegisteredClientRepository(RecordingIndex(), store, UUIDUtil())

        repository.save(client)
        repository.save(client)

        Assertions.assertThat(store.minted).hasSize(1)
        Assertions.assertThat(store.added.map { it.key }).containsOnly(store.minted.single())
    }

    @Test
    fun `a store that mints no key fails the save`() {
        val store = RecordingStore(mints = false)

        Assertions
            .assertThatThrownBy {
                KeyValueStoreRegisteredClientRepository(RecordingIndex(), store, UUIDUtil()).save(client)
            }
            .hasMessageContaining("minted no key")
        Assertions.assertThat(store.added).isEmpty()
    }
}
