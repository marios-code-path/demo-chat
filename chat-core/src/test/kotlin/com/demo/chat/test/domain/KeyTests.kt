package com.demo.chat.test.domain

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageKey
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestKey
import com.demo.chat.test.TestMessageKey
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher
import java.time.Duration
import java.util.*
import java.util.stream.Stream

class KeyTests : TestBase() {
    @Test
    fun `should test streaming only through publisher`() {
        val messagePub = TestPublisher.create<Key<out Any>>()
        val messageFlux = messagePub.flux()

        StepVerifier
                .create(messageFlux)
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