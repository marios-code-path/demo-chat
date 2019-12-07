package com.demo.chatevents.tests

import com.demo.chat.domain.EventKey
import com.demo.chat.service.BooleanTopicService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.*

open class BooleanTopicTestBase() {

    lateinit var svc: BooleanTopicService<EventKey, String>

    @Test
    fun `should put item into topic`() {
        val topicKey = EventKey.create(UUID.randomUUID())
        val myId = UUID.randomUUID().toString()

        StepVerifier.create(
                svc
                        .add(topicKey, myId)
        )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }

    @Test
    fun `should put and receive item into topic`() {
        val topicKey = EventKey.create(UUID.randomUUID())
        val myId = UUID.randomUUID().toString()

        StepVerifier.create(
                svc
                        .add(topicKey, myId)
                        .thenMany(
                                svc.compute(topicKey)
                        )
        )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .isNotEmpty
                            .contains(myId)
                }
                .verifyComplete()
    }

    @Test
    fun `should put twice to remove`() {
        val topicKey = EventKey.create(UUID.randomUUID())
        val myId = UUID.randomUUID().toString()

        StepVerifier.create(
                Flux
                        .concat(
                                svc.add(topicKey, myId),
                                svc.add(topicKey, myId)
                        )
                        .thenMany(
                                svc.compute(topicKey)
                        )
        )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .isEmpty()
                }
    }

    val fnUUIDStr = { UUID.randomUUID().toString() }

    @Test
    fun `should puts be entry safe`() {
        val topicKey = EventKey.create(UUID.randomUUID())
        val myId = fnUUIDStr()
        val anotherId = fnUUIDStr()

        StepVerifier.create(
                Flux
                        .concat(
                                svc.add(topicKey, myId),
                                svc.add(topicKey, myId),
                                svc.add(topicKey, anotherId)
                        )
                        .thenMany(
                                svc.compute(topicKey)
                        )
        )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .isNotEmpty
                            .contains(anotherId)
                            .doesNotContain(myId)
                }
    }

    @Test
    fun `should reset to key where reduce is empty`() {
        val topicKey = EventKey.create(UUID.randomUUID())
        val myId = fnUUIDStr()
        val anotherId = fnUUIDStr()
        StepVerifier.create(
                Flux
                        .concat(
                                svc.add(topicKey, myId),
                                svc.add(topicKey, myId),
                                svc.add(topicKey, anotherId)
                        )
                        .then(svc.add(topicKey, myId))
                        .map {
                            svc.reset(topicKey, it)
                        }
                        .thenMany(
                                svc.compute(topicKey)
                        )
        )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .isEmpty()
                }
    }

    @Test
    fun `should reduce be safe after reset`() {
        val topicKey = EventKey.create(UUID.randomUUID())
        val myId = fnUUIDStr()
        val anotherId = fnUUIDStr()

        StepVerifier.create(
                Flux
                        .concat(
                                svc.add(topicKey, myId),
                                svc.add(topicKey, myId),
                                svc.add(topicKey, anotherId)
                        )
                        .then(svc.add(topicKey, myId))
                        .map {
                            svc.reset(topicKey, it)
                        }
                        .then(svc.add(topicKey, myId))
                        .thenMany(
                                svc.compute(topicKey)
                        )
        )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .contains(myId)
                }
    }
}