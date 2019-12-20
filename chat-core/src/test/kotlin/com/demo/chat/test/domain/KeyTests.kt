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
    fun `T Key serialize deserialize`() {
        val keyJsons = Flux.just<Key<out Any>>(
                MessageKey.create(3,4),
                MessageKey.create("a", "b")
        )
                .map(mapper::writeValueAsString)
                .map<TestMessageKey<out Any>>(mapper::readValue)

        StepVerifier
                .create(keyJsons)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.id)
                            .isInstanceOf(Integer::class.java)
                }
                .assertNext {

                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.id)
                            .isInstanceOf(String::class.java)
                }
                .expectComplete()
                .verify(Duration.ofMillis(500))
    }

    @Test
    fun `Any Key serialize deserialize`() {
        val keyJsons = ArrayList<String>()
        val keys = ArrayList<Key<out Any>>()

        Stream.generate { randomAnyKey() }.limit(5)
                .forEach { msg ->
                    keyJsons.add(mapper.writeValueAsString(msg))
                }

        keyJsons
                .forEach { json ->
                    val tree = mapper.readTree(json)

                    if (tree.fieldNames().hasNext()) {
                        when (tree.fieldNames().next()) {
                            "Key" -> keys.add(mapper.readValue<TestKey<out Any>>(json))
                        }
                    }
                }

        Assertions.assertThat(keys)
                .`as`("A collection of Keys is present")
                .isNotNull
                .isNotEmpty

        keys
                .forEach { key ->
                    Assertions.assertThat(key)
                            .`as`("Is a key")
                            .isNotNull
                            .hasFieldOrProperty("id")
                }
    }

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