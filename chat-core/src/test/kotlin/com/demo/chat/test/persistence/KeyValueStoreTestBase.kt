package com.demo.chat.test.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.service.core.KeyValueStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.function.Supplier

open class KeyValueStoreTestBase<K, V>(
    val valCodec: Supplier<KeyValuePair<K, V>>,
    val valType: Supplier<Class<*>>,
    val store: KeyValueStore<K, V>,//PersistenceStore<K, KeyValuePair<K, V>>,
    val keyFromEntity: (KeyValuePair<K, *>) -> Key<K>,
) {

    @Test
    fun `add, typedGet, equality`() {
        val thing = valCodec.get()

        StepVerifier.create(
            store.key()
                .flatMap { store.add(thing) }
                .then(store.typedGet(keyFromEntity(thing), valType.get()))
        )
            .assertNext {
                Assertions.assertThat(it).isEqualTo(thing)
            }
            .verifyComplete()
    }

    @Test
    fun `add, all, typedByIds`() {
        val byIds = store.add(valCodec.get())
            .thenMany(store.all())
            .collectList()
            .flatMapMany { list ->
                val ids = list.map { keyFromEntity(it) }
                store.byIds(ids)
            }

        StepVerifier.create(byIds)
            .expectNextCount(1)
            .verifyComplete()
    }

    @Test
    fun `add, typedAll`() {
        val saveNFind = store
            .add(valCodec.get())
            .thenMany(store.typedAll(valType.get()))

        StepVerifier.create(saveNFind)
            .expectSubscription()
            .assertNext {
                Assertions.assertThat(it)
                    .isNotNull
            }
            .verifyComplete()
    }
}