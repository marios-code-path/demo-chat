package com.demo.chat.test.index

import com.demo.chat.domain.Key
import com.demo.chat.service.IndexService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Duration
import java.util.function.Function
import java.util.function.Supplier

@Disabled
abstract class IndexTests<T, E, Q>(
        val myIndex: IndexService<T, E, Q>,
        val valueSupply: Supplier<E>,
        val keyExtract: Function<E, Key<T>>,
        val querySupply: Supplier<Q>
) {
    abstract fun getIndex(): IndexService<T, E, Q>

    @Test
    fun `should save one`() {
        StepVerifier
                .create(getIndex().add(valueSupply.get()))
                .verifyComplete()
    }

    @Test
    fun `should remove one`() {
        StepVerifier
                .create(
                        getIndex().rem(keyExtract.apply(valueSupply.get())))
                .verifyComplete()
    }

    @Test
    fun `should save and remove and query with no result`() {
        val doc = valueSupply.get()
        val key = keyExtract.apply(doc)

        val cb = getIndex().add(doc)
                .then(getIndex().rem(key))
                .thenMany(getIndex().findBy(querySupply.get()))

        StepVerifier
                .create(cb)
                .verifyComplete()
    }

    @Test
    fun `should save and find many`() {
        val bar = getIndex()
            .add(valueSupply.get())
            .and(getIndex().add(valueSupply.get()))
            .thenMany(getIndex().findBy(querySupply.get()))

        StepVerifier
            .create(bar)
            .assertNext { key ->
                Assertions
                    .assertThat(key)
                    .isNotNull
            }
            .assertNext { key ->
                Assertions
                    .assertThat(key)
                    .isNotNull
            }
            .verifyComplete()
    }

    @Test
    fun `should save and fine one`() {
        val bar = getIndex()
                .add(valueSupply.get())
                .thenMany(getIndex().findUnique(querySupply.get()))

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