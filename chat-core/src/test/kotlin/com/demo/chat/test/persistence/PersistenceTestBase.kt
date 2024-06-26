package com.demo.chat.test.persistence

import com.demo.chat.domain.Key
import com.demo.chat.service.core.PersistenceStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.mockito.kotlin.mockingDetails
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.function.Supplier

open class KeyAwarePersistenceTestBase<K, V>(
    val v: Supplier<V>,
    val s: PersistenceStore<K, V>,
    val keyFromEntity: (V) -> Key<K>,
) : PersistenceTestBase<K, V>(v, s) {

    @Test
    fun `add one find one`() {
        val thing = valCodec.get()

        StepVerifier
            .create(
                store.key()
                    .flatMap { store.add(thing) }
                    .then(store.get(keyFromEntity(thing)))
            )
            .assertNext {
                Assertions.assertThat(it).isEqualTo(thing)
            }
            .verifyComplete()
    }

    @Test
    fun `add one find by ids`() {

        val byIds = store.add(valCodec.get())
            .thenMany(store.all())
            .collectList()
            .flatMapMany { list ->
                val ids = list.map { keyFromEntity(it) }
                store.byIds(ids)
            }

        StepVerifier
            .create(byIds)
            .expectNextCount(1)
            .verifyComplete()

    }
}

@Disabled
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
open class PersistenceTestBase<K, V>(
    val valCodec: Supplier<V>,
    val store: PersistenceStore<K, V>,
) {

    @Test
    fun `key gen`() {
        StepVerifier
            .create(store.key())
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasNoNullFieldsOrProperties()
            }
            .verifyComplete()
    }

    @Test
    fun `add single, finds all`() {
        if (mockingDetails(store).isMock)
            BDDMockito.given(store.all())
                .willReturn(Flux.just(valCodec.get()))

        val saveNFind = store
            .add(valCodec.get())
            .thenMany(store.all())

        StepVerifier
            .create(saveNFind)
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
            }
            .verifyComplete()
    }

    @Test
    fun `delete one`() {
        StepVerifier
            .create(store.key().flatMap(store::rem))
            .verifyComplete()
    }

    @Test
    fun `does not exist`() {
        val keyPub = store.key()
            .flatMap { k ->
                store
                    .rem(k)
                    .then(store.get(k))
            }

        StepVerifier
            .create(keyPub)
            .verifyComplete()
    }

}