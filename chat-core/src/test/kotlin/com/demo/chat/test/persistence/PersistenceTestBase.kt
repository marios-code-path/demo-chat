package com.demo.chat.test.persistence

import com.demo.chat.service.PersistenceStore
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.given
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.function.Supplier

@Disabled
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
    fun `gets all empty`() {
        given(store.all())
                .willReturn(Flux.empty())

        val all = store.all()

        StepVerifier
                .create(all)
                .expectSubscription()
                .verifyComplete()
    }

    @Test
    fun `add single, finds all`() {
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
        given(store.get(any()))
                .willReturn(Mono.empty())
        StepVerifier
                .create(store.key().flatMap(store::get))
                .verifyComplete()
    }
}