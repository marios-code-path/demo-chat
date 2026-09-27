package com.demo.chat.test.domain

import com.demo.chat.test.key.TestKeys

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
        val k1 = TestKeys.key(1L)
        val k2 = TestKeys.key(1L)

        Assertions
            .assertThat(k1)
            .isEqualTo(k2)
    }

    @Test
    fun `key equality should be different`() {
        val k1 = TestKeys.key(1L)
        val k2 = TestKeys.key(1)

        Assertions
            .assertThat(k1)
            .isNotEqualTo(k2)

        val k3 = TestKeys.key("1".toLong())

        Assertions
            .assertThat(k1)
            .isEqualTo(k3)

        Assertions
            .assertThat(k1 == k3)
            .isTrue
    }

    @Test
    fun `equal keys should share a hash code`() {
        Assertions
            .assertThat(TestKeys.key(1L).hashCode())
            .isEqualTo(TestKeys.key(1L).hashCode())

        Assertions
            .assertThat(TestKeys.empty(1L).hashCode())
            .isEqualTo(TestKeys.empty(1L).hashCode())
    }

    @Test
    fun `a map keyed on a key should find an equal key`() {
        val map = hashMapOf(TestKeys.key(1L) to "found")

        Assertions
            .assertThat(map[TestKeys.key(1L)])
            .isEqualTo("found")

        val emptyMap = hashMapOf(TestKeys.empty(1L) to "found")

        Assertions
            .assertThat(emptyMap[TestKeys.empty(1L)])
            .isEqualTo("found")
    }

    @Test
    fun `an empty key should not equal a populated key in either direction`() {
        val populated = TestKeys.key(1L)
        val empty = TestKeys.empty(1L)

        Assertions
            .assertThat(populated)
            .isNotEqualTo(empty)

        Assertions
            .assertThat(empty)
            .isNotEqualTo(populated)
    }

    @Test
    fun `should create`() {
        Assertions
            .assertThat(TestKeys.key("TEST"))
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
            TestKeys.key(id)
        else
            TestKeys.key(counter)
    }
}