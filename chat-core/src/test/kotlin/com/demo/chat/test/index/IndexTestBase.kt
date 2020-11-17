package com.demo.chat.test.index

import com.demo.chat.domain.Key
import com.demo.chat.service.IndexService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Duration
import java.util.function.Supplier

@Disabled
open class IndexTestBase<T, E, Q>(
        val valueSupply: Supplier<E>,
        val keySupply: Supplier<Key<T>>,
        val querySupply: Supplier<Q>,
        val testIndex: IndexService<T, E, Q>
) {

    @Test
    fun `should save one`() {
        StepVerifier
                .create(testIndex.add(valueSupply.get()))
                .verifyComplete()
    }

    @Test
    fun `should remove one`() {
        StepVerifier
                .create(
                        testIndex.rem(keySupply.get()))
                .verifyComplete()
    }

    @Test
    fun `should save and remove and query with no result`() {
        val cb = testIndex.add(valueSupply.get())
                .then(testIndex.rem(keySupply.get()))
                .thenMany(testIndex.findBy(querySupply.get()))

        StepVerifier
                .create(cb)
                .verifyComplete()
    }

    @Test
    fun `should save and fine one`() {

        val bar = testIndex
                .add(valueSupply.get())
                .thenMany(testIndex.findBy(querySupply.get()))

        StepVerifier
                .create(bar)
                .assertNext { key ->
                    Assertions
                            .assertThat(key)
                            .isNotNull
                }
                .verifyComplete()
    }
}