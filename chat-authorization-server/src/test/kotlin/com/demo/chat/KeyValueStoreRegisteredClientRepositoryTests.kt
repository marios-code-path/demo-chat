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

    private class RecordingStore : KeyValueStore<UUID, Any> {
        val added = mutableListOf<KeyValuePair<UUID, Any>>()

        override fun key(): Mono<out Key<UUID>> = Mono.empty()
        override fun add(ent: KeyValuePair<UUID, Any>): Mono<Void> =
            Mono.fromRunnable { added.add(ent) }

        override fun rem(key: Key<UUID>): Mono<Void> = Mono.empty()
        override fun get(key: Key<UUID>): Mono<out KeyValuePair<UUID, Any>> = Mono.empty()
        override fun all(): Flux<out KeyValuePair<UUID, Any>> = Flux.empty()
    }

    private class RecordingIndex : KeyValueIndexService<UUID, IndexSearchRequest> {
        val added = mutableListOf<KeyValuePair<UUID, Any>>()

        override fun add(entity: KeyValuePair<UUID, Any>): Mono<Void> =
            Mono.fromRunnable { added.add(entity) }

        override fun rem(key: Key<UUID>): Mono<Void> = Mono.empty()
        override fun findBy(query: IndexSearchRequest): Flux<out Key<UUID>> = Flux.empty()
        override fun findUnique(query: IndexSearchRequest): Mono<out Key<UUID>> = Mono.empty()
    }

    @Test
    fun `save subscribes the store write and the index write`() {
        val store = RecordingStore()
        val index = RecordingIndex()

        KeyValueStoreRegisteredClientRepository(index, store, UUIDUtil()).save(client)

        Assertions.assertThat(store.added).hasSize(1)
        Assertions.assertThat(index.added).hasSize(1)
        Assertions.assertThat(store.added.first().key.id).isEqualTo(clientId)
        Assertions.assertThat(index.added.first().data).isEqualTo(client)
    }
}
