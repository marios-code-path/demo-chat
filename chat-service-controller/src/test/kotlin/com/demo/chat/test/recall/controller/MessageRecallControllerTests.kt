package com.demo.chat.test.recall.controller

import com.demo.chat.controller.composite.mapping.MessageRecallControllerMapping
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.service.vector.MessageRecallHit
import com.demo.chat.service.vector.MessageRecallService
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketServerTestConfiguration
import com.demo.chat.test.rsocket.RSocketTestBase
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.stereotype.Controller
import org.springframework.test.context.ContextConfiguration
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * The test controller lives outside com.demo.chat.test.rsocket. The
 * component scan in RSocketServerTestConfiguration covers that package, so a
 * controller inside it enters every RSocket test context. The scan still
 * brings in the secrets test controller, and the mock below satisfies it.
 */
@ContextConfiguration(
    classes = [
        TestMessageRecallController::class,
        RSocketServerTestConfiguration::class,
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageRecallControllerTests : RSocketTestBase("user", "password") {

    @MockBean
    private lateinit var recallService: MessageRecallService<Long>

    @MockBean
    private lateinit var secretsStore: SecretsStore<Long>

    @Test
    fun `recall topic route returns hits over the shared DTOs`() {
        BDDMockito
            .given(recallService.recallInTopic(anyObject()))
            .willReturn(
                Flux.just(
                    MessageRecallHit(MessageKey.create(10L, 20L, 30L), 0.9)
                )
            )

        StepVerifier.create(
            requester
                .route("message-recall-topic")
                .data(Mono.just(TopicRecallRequest(30L, "apple")), TopicRecallRequest::class.java)
                .retrieveFlux(MessageRecallHit::class.java)
        )
            .assertNext { hit ->
                Assertions.assertThat(hit.score).isEqualTo(0.9)
                Assertions.assertThat(hit.key.id).isEqualTo(10L)
                Assertions.assertThat(hit.key.from).isEqualTo(20L)
                Assertions.assertThat(hit.key.dest).isEqualTo(30L)
            }
            .verifyComplete()
    }
}

@Controller
class TestMessageRecallController<T>(private val that: MessageRecallService<T>) :
    MessageRecallControllerMapping<T>, MessageRecallService<T> by that
