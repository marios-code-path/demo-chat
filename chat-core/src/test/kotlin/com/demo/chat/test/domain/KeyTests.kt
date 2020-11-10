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
                    messagePub.next(randomAnyKey())
                    messagePub.next(randomAnyKey())
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

    private fun randomAnyKey(): Key<out Any> {

        val id = UUID.randomUUID()
        counter++

        return if (counter % 2 == 0)
            Key.anyKey(id)
        else
            Key.anyKey(counter)
    }
}