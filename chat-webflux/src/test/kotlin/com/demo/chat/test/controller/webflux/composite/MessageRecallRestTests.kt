package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.controller.webflux.ChatMessageRecallController
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.vector.MessageRecallHit
import com.demo.chat.service.vector.MessageRecallService
import com.demo.chat.test.anyObject
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux

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

    @Autowired
    private lateinit var mapper: ObjectMapper

    @MockBean
    private lateinit var recallService: MessageRecallService<Long>

    @Test
    fun `recall topic returns NDJSON hits`() {
        BDDMockito
            .given(recallService.recallInTopic(anyObject()))
            .willReturn(
                Flux.just(
                    MessageRecallHit(MessageKey.create(10L, 20L, 30L), 0.9),
                    MessageRecallHit(MessageKey.create(11L, 20L, 30L), 0.5),
                )
            )

        val response = client
            .post()
            .uri("/message/recall/topic")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type":"TopicRecallRequest","topicId":30,"query":"apple"}""")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
            .expectBody(String::class.java)
            .returnResult()

        val lines = response.responseBody!!.trim().lines()
        Assertions.assertThat(lines).hasSize(2)
        val first = mapper.readValue<MessageRecallHit<Long>>(lines[0])
        Assertions.assertThat(first.key.id).isEqualTo(10L)
        Assertions.assertThat(first.key.from).isEqualTo(20L)
        Assertions.assertThat(first.key.dest).isEqualTo(30L)
        Assertions.assertThat(first.score).isEqualTo(0.9)
    }

    @Test
    fun `recall user and global routes exist`() {
        BDDMockito
            .given(recallService.recallByUser(anyObject()))
            .willReturn(Flux.just(MessageRecallHit(MessageKey.create(10L, 20L, 30L), 0.9)))
        BDDMockito
            .given(recallService.recallGlobal(anyObject()))
            .willReturn(Flux.just(MessageRecallHit(MessageKey.create(10L, 20L, 30L), 0.9)))

        client.post().uri("/message/recall/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type":"UserRecallRequest","userId":20,"query":"apple"}""")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)

        client.post().uri("/message/recall/global")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type":"GlobalRecallRequest","query":"apple"}""")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
    }
}
