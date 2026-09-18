package com.demo.chat.test.rsocket.controller.core

import com.demo.chat.client.rsocket.clients.core.KeyValueStoreClient
import com.demo.chat.controller.core.PersistenceServiceController
import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.vector.IndexJobCodec
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketTestBase
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

/**
 * The client reads with get and decodes locally. It never calls typedGet,
 * whose route carries only the key, so the server receives no class and cannot
 * bind the value.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(KeyValueJobDecodeRequesterTests.KeyValueStoreTestConfiguration::class)
class KeyValueJobDecodeRequesterTests : RSocketTestBase() {

    @MockitoBean
    private lateinit var keyValueStore: KeyValueStore<Long, Any>

    private val svcPrefix = ""

    // The deployed mapper. RSocketServerTestConfiguration enables auto
    // configuration and imports TestModules, so this one carries the chat
    // Jackson modules exactly as a deployment does.
    @Autowired
    private lateinit var mapper: ObjectMapper

    private val key = Key.funKey(1000L)

    private val job = IndexJob(
        key = key,
        nodeId = 7,
        keyType = "long",
        incarnationId = "incarnation-a",
        startedBy = Key.funKey(2000L),
        startedAt = Instant.parse("2026-09-12T12:00:00Z"),
        outcome = JobOutcome.SUCCEEDED,
    )

    @Test
    fun `a job decodes through the key value client`() {
        BDDMockito
            .given(keyValueStore.get(anyObject()))
            .willReturn(Mono.just(KeyValuePair.create(key, job as Any)))

        val client = KeyValueStoreClient<Long>(svcPrefix, requester)

        StepVerifier
            .create(client.get(key))
            .assertNext { pair ->
                val decoded = IndexJobCodec<Long>(mapper).decode(pair.data)

                Assertions.assertThat(decoded.outcome).isEqualTo(JobOutcome.SUCCEEDED)
                Assertions.assertThat(decoded.nodeId).isEqualTo(7)
                Assertions.assertThat(decoded.incarnationId).isEqualTo("incarnation-a")
            }
            .verifyComplete()
    }

    // @TestConfiguration is required. A plain imported class does not have its
    // nested controller discovered, so the routes would never register.
    @TestConfiguration
    class KeyValueStoreTestConfiguration {
        @Controller
        class TestKeyValueController<T>(
            store: KeyValueStore<T, Any>,
        ) : PersistenceServiceController<T, KeyValuePair<T, Any>>(store)
    }
}
