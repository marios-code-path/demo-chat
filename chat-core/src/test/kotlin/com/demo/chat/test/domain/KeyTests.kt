package com.demo.chat.test.domain

import com.demo.chat.domain.Key
import com.demo.chat.test.TestBase
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher
import java.time.Duration
import java.util.*

class KeyTests : TestBase() {

    @Test
    fun `key equality should be the same`() {
        val k1 = Key.funKey(1L)
        val k2 = Key.funKey(1L)

        Assertions
            .assertThat(k1)
            .isEqualTo(k2)
    }

    @Test
    fun `key equality should be different`() {
        val k1 = Key.funKey(1L)
        val k2 = Key.funKey(2L)

        Assertions
            .assertThat(k1)
            .isNotEqualTo(k2)
    }

    @Test
    fun `should create`() {
        Assertions
            .assertThat(Key.funKey("TEST"))
            .isNotNull
            .hasNoNullFieldsOrProperties()
    }

    @Test
    fun `should test streaming only through publisher`() {
        val messagePub = TestPublisher.create<Key<*>>()

        StepVerifier
            .create(messagePub)
            .expectSubscription()
            .then {
                messagePub.next(randomFunKey())
                messagePub.next(randomFunKey())
            }
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
            }
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
            }
            .then {
                messagePub.complete()
            }
            .expectComplete()
            .verify(Duration.ofSeconds(1))
    }

    private fun randomFunKey(): Key<out Any> {

        val id = UUID.randomUUID()
        counter++

        return if (counter % 2 == 0)
            Key.funKey(id)
        else
            Key.funKey(counter)
    }
}