package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.controller.webflux.ChatMessageRecallController
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.vector.MessageRecallHit
import com.demo.chat.service.vector.MessageRecallResult
import com.demo.chat.service.vector.MessageRecallService
import com.demo.chat.test.anyObject
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

/**
 * REST recall routes. The module has no mockito-kotlin dependency, so the
 * tests use the repository helper anyObject.
 */
@WebFluxTest
@ContextConfiguration(
    classes = [WebFluxTestConfiguration::class, ChatMessageRecallController::class]
)
@TestPropertySource(properties = ["app.controller.recall"])
class MessageRecallRestTests {

    @Autowired
    private lateinit var client: WebTestClient

    @MockBean
    private lateinit var recallService: MessageRecallService<Long>

    @Test
    fun `recall topic returns one object with the flag`() {
        BDDMockito
            .given(recallService.recallInTopic(anyObject()))
            .willReturn(
                Mono.just(
                    MessageRecallResult(
                        indexComplete = true,
                        hits = listOf(
                            MessageRecallHit(MessageKey.create(10L, 20L, 30L), 0.9),
                            MessageRecallHit(MessageKey.create(11L, 20L, 30L), 0.5),
                        ),
                    )
                )
            )

        client
            .post()
            .uri("/message/recall/topic")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type":"TopicRecallRequest","topicId":30,"query":"apple"}""")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.indexComplete").isEqualTo(true)
            .jsonPath("$.hits.length()").isEqualTo(2)
            // Key carries a WRAPPER_OBJECT named key, so the id sits one level
            // deeper than the field name suggests. KeyValuePair.kt declares it.
            .jsonPath("$.hits[0].key.key.id").isEqualTo(10)
    }

    // The empty case is the reason this contract changed. A stream of hits
    // cannot carry a flag when it carries no hit.
    @Test
    fun `an empty recall still carries the flag`() {
        BDDMockito
            .given(recallService.recallGlobal(anyObject()))
            .willReturn(Mono.just(MessageRecallResult(indexComplete = false, hits = emptyList())))

        client
            .post()
            .uri("/message/recall/global")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type":"GlobalRecallRequest","query":"apple"}""")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.indexComplete").isEqualTo(false)
            .jsonPath("$.hits.length()").isEqualTo(0)
    }

    @Test
    fun `recall user returns one object`() {
        BDDMockito
            .given(recallService.recallByUser(anyObject()))
            .willReturn(
                Mono.just(
                    MessageRecallResult(
                        indexComplete = true,
                        hits = listOf(MessageRecallHit(MessageKey.create(12L, 20L, 30L), 0.7)),
                    )
                )
            )

        client
            .post()
            .uri("/message/recall/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type":"UserRecallRequest","userId":20,"query":"apple"}""")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.hits.length()").isEqualTo(1)
            .jsonPath("$.hits[0].key.key.id").isEqualTo(12)
    }

    // An absent media type does not stop NDJSON. Content negotiation would
    // answer this request with NDJSON, and the specification says these routes
    // no longer produce it. Each route names JSON, so this request gets 406.
    //
    // The service mock holds no stub here. A handler that ran would answer with
    // a null Mono and 500, so the status separates the two outcomes.
    @Test
    fun `the topic route refuses a request for NDJSON`() {
        client
            .post()
            .uri("/message/recall/topic")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_NDJSON)
            .bodyValue("""{"type":"TopicRecallRequest","topicId":30,"query":"apple"}""")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.NOT_ACCEPTABLE)
    }

    @Test
    fun `the user route refuses a request for NDJSON`() {
        client
            .post()
            .uri("/message/recall/user")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_NDJSON)
            .bodyValue("""{"type":"UserRecallRequest","userId":20,"query":"apple"}""")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.NOT_ACCEPTABLE)
    }

    @Test
    fun `the global route refuses a request for NDJSON`() {
        client
            .post()
            .uri("/message/recall/global")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_NDJSON)
            .bodyValue("""{"type":"GlobalRecallRequest","query":"apple"}""")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.NOT_ACCEPTABLE)
    }
}
