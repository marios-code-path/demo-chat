package com.demo.chat.deploy.tests

import com.demo.chat.persistence.consul.ConsulKVStore
import com.ecwid.consul.v1.ConsulClient
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
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
    fun `should write a name`() {
        StepVerifier
            .create(kvStore.write("test", "test"))
            .verifyComplete()
    }

    @Test
    fun `should write names and list them`() {
        val names = kvStore.write("list/1", "test")
            .then(kvStore.write("list/1", "test"))
            .then(kvStore.write("list/2", "test"))
            .thenMany(kvStore.names())
            .filter { it.startsWith("TEST/list/") }

        StepVerifier
            .create(names)
            .expectNextCount(2)
            .verifyComplete()
    }

    @Test
    fun `should write then remove a name`() {
        val value = kvStore.write("gone", "test")
            .then(kvStore.remove("gone"))
            .then(kvStore.read("gone"))

        StepVerifier
            .create(value)
            .verifyComplete()
    }

    @Test
    fun `should read the value it wrote`() {
        val value = kvStore.write("read/1", "test")
            .then(kvStore.read("read/1"))

        StepVerifier
            .create(value)
            .expectNext("test")
            .verifyComplete()
    }
}
