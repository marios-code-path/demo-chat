package com.demo.chat.deploy.tests

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyDataPair
import com.demo.chat.persistence.consul.ConsulKVStore
import com.ecwid.consul.v1.ConsulClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsulKVStoreTests : ConsulContainerSetup() {

    @Value("\${spring.cloud.consul.port}")
    lateinit var port: String

    lateinit var client: ConsulClient
    lateinit var kvStore: ConsulKVStore

    @BeforeAll
    fun setUp() {
        client = ConsulClient("localhost", port.toInt())
        kvStore = ConsulKVStore(client,"TEST")
    }

    @Test
    fun `should set key`() {
        StepVerifier
            .create(kvStore.add(KeyDataPair.create(Key.funKey("test"), "test")))
            .verifyComplete()
    }

    @Test
    fun `should set keys and list`() {
        val kvKeys = kvStore
            .add(KeyDataPair.create(Key.funKey("test/1"), "test"))
            .then(kvStore.add(KeyDataPair.create(Key.funKey("test/1"), "test")))
            .then(kvStore.add(KeyDataPair.create(Key.funKey("test/2"), "test")))
            .thenMany(kvStore.all())

        StepVerifier
            .create(kvKeys)
            .expectNextCount(3)
            .verifyComplete()
    }

    @Test
    fun `should add then remove key`() {
        val kvProcess = kvStore
            .add(KeyDataPair.create(Key.funKey("test/1"), "test"))
            .then(kvStore.rem(Key.funKey("test/1")))
            .thenMany(kvStore.all())

        StepVerifier
            .create(kvProcess)
            .expectNextCount(0)
            .verifyComplete()
    }

    @Test
    fun `should get Key and validate contents`() {
        val kvProcess = kvStore
            .add(KeyDataPair.create(Key.funKey("test/1"), "test"))
            .then(kvStore.get(Key.funKey("test/1")))

        StepVerifier
            .create(kvProcess)
            .assertNext { kv ->
                Assertions
                    .assertThat(kv)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("data", "test")
            }
            .verifyComplete()
    }
}