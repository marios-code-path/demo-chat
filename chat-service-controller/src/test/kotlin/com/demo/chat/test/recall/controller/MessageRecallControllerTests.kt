package com.demo.chat.test.recall.controller

import com.demo.chat.controller.composite.mapping.MessageRecallControllerMapping
import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.UserRecallRequest
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.service.vector.MessageRecallHit
import com.demo.chat.service.vector.MessageRecallResult
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
    fun `the topic route returns one result`() {
        BDDMockito
            .given(recallService.recallInTopic(anyObject()))
            .willReturn(
                Mono.just(
                    MessageRecallResult(
                        indexComplete = true,
                        hits = listOf(MessageRecallHit(MessageKey.create(10L, 20L, 30L), 0.9)),
                    )
                )
            )

        StepVerifier
            .create(
                requester
                    .route("message-recall-topic")
                    .data(TopicRecallRequest(30L, "apple", 10, 0.0))
                    .retrieveMono(MessageRecallResult::class.java)
            )
            .assertNext { result ->
                Assertions.assertThat(result.indexComplete).isTrue()
                Assertions.assertThat(result.hits).hasSize(1)
                Assertions.assertThat(result.hits[0].key.id).isEqualTo(10L)
                Assertions.assertThat(result.hits[0].score).isEqualTo(0.9)
            }
            .verifyComplete()
    }

    @Test
    fun `the user route returns one result`() {
        BDDMockito
            .given(recallService.recallByUser(anyObject()))
            .willReturn(Mono.just(MessageRecallResult(indexComplete = false, hits = emptyList())))

        StepVerifier
            .create(
                requester
                    .route("message-recall-user")
                    .data(UserRecallRequest(20L, "apple", 10, 0.0))
                    .retrieveMono(MessageRecallResult::class.java)
            )
            .assertNext { result ->
                Assertions.assertThat(result.indexComplete).isFalse()
                Assertions.assertThat(result.hits).isEmpty()
            }
            .verifyComplete()
    }

    // One response, not a stream. A stream of one would still decode here, so
    // the assertion that matters is the single completion above and the route
    // signature itself.
    @Test
    fun `the global route returns one result`() {
        BDDMockito
            .given(recallService.recallGlobal(anyObject()))
            .willReturn(
                Mono.just(
                    MessageRecallResult(
                        indexComplete = true,
                        hits = listOf(MessageRecallHit(MessageKey.create(11L, 20L, 30L), 0.4)),
                    )
                )
            )

        StepVerifier
            .create(
                requester
                    .route("message-recall-global")
                    .data(GlobalRecallRequest("apple", 10, 0.0))
                    .retrieveMono(MessageRecallResult::class.java)
            )
            .assertNext { result -> Assertions.assertThat(result.hits).hasSize(1) }
            .verifyComplete()
    }
}

@Controller
class TestMessageRecallController<T>(private val that: MessageRecallService<T>) :
    MessageRecallControllerMapping<T>, MessageRecallService<T> by that
