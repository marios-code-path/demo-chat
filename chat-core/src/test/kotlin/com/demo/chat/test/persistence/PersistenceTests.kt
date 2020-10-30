package com.demo.chat.test.persistence

import com.demo.chat.codec.Codec
import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import com.demo.chat.service.PersistenceStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier

open class PersistenceTests<K, V>(
        val valCodec: Codec<Unit, V>,
        val store: PersistenceStore<K, V>) {

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
    fun `gets all`() {
        val all = store.all()

        StepVerifier
                .create(all)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }

    @Test
    fun `add single, finds all`() {
        val saveNFind = store
                .add(valCodec.decode(Unit))
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
    fun `get one`() {
        StepVerifier
                .create(store.key().flatMap(store::get))
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }
}