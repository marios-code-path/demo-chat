package com.demo.chat.test.rsocket.controller.core

import com.demo.chat.controller.core.IndexServiceController
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.service.core.KeyValueIndexService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketTestBase
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    KVIndexTestConfiguration::class
)
class KeyValueIndexRequesterTests : RSocketTestBase() {
//    @MockBean
//    private lateinit var indexService: MessageIndexService<UUID, String, IndexSearchRequest>
//
//    private val message =
//        Message.create(MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), "TEST", true)

    @MockBean
    private lateinit var indexService: KeyValueIndexService<UUID, IndexSearchRequest>

    private val kvData =
        KeyValuePair.create(Key.funKey(UUID.randomUUID()), "DATA")

    @Test
    fun `should query for entities`() {
        BDDMockito
            .given(indexService.findBy(anyObject()))
            .willReturn(Flux.just(kvData.key))

        StepVerifier
            .create(
                requester
                    .route("query")
                    .data(IndexSearchRequest(MessageIndexService.TOPIC, UUID.randomUUID().toString(), 100))
                    .retrieveFlux(Key::class.java)
            )
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasNoNullFieldsOrProperties()
                    .hasFieldOrProperty("id")
                    .hasFieldOrPropertyWithValue("id", kvData.key.id)
            }
            .verifyComplete()
    }

}

@TestConfiguration
class KVIndexTestConfiguration {
    @Controller
    class TestKVIndexController<T>(that: KeyValueIndexService<T, IndexSearchRequest>) :
        IndexServiceController<T, KeyValuePair<T, Any>, IndexSearchRequest>(that)
}