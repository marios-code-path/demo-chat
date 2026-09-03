package com.demo.chat.test.service.composite

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.InvalidRecallRequestException
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.UserRecallRequest
import com.demo.chat.service.composite.impl.MessageRecallServiceImpl
import com.demo.chat.service.vector.MessageDocumentMapper
import com.demo.chat.service.vector.MessageRecallHit
import com.demo.chat.test.vector.MockVectorStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class MessageRecallServiceImplTests {

    private val store = MockVectorStore()
    private val mapper = MessageDocumentMapper<Long>(LongUtil(), "long")
    private val service = MessageRecallServiceImpl<Long>(store, LongUtil(), "long")
    private val parser = FilterExpressionTextParser()

    @BeforeEach
    fun seed() {
        store.add(
            listOf(
                mapper.toDocument(Message.create(MessageKey.create(1L, 10L, 100L), "apple banana", true)),
                mapper.toDocument(Message.create(MessageKey.create(2L, 10L, 100L), "apple pie", true)),
                mapper.toDocument(Message.create(MessageKey.create(3L, 20L, 100L), "zebra stripe", true)),
                mapper.toDocument(Message.create(MessageKey.create(4L, 20L, 200L), "apple banana cake", true)),
            )
        )
    }

    private fun hits(source: Flux<MessageRecallHit<Long>>): List<MessageRecallHit<Long>> =
        source.collectList().block()!!

    @Test
    fun `topic recall builds the topic filter and returns only that topic`() {
        val found = hits(service.recallInTopic(TopicRecallRequest(100L, "apple banana")))

        Assertions.assertThat(store.lastFilter)
            .isEqualTo(parser.parse("kind == 'message' && keyType == 'long' && topicId == '100'"))
        Assertions.assertThat(store.lastTopK).isEqualTo(10)
        Assertions.assertThat(store.lastThreshold).isEqualTo(0.0)
        // Seeds 1, 2, and 3 are all in topic 100.
        Assertions.assertThat(found.map { it.key.dest }).containsOnly(100L)
        Assertions.assertThat(found.map { it.key.id }).containsExactlyInAnyOrder(1L, 2L, 3L)
        Assertions.assertThat(found).allSatisfy { Assertions.assertThat(it.score).isNotNull() }
    }

    @Test
    fun `user recall builds the user filter and returns only that user`() {
        val found = hits(service.recallByUser(UserRecallRequest(10L, "apple banana")))

        Assertions.assertThat(store.lastFilter)
            .isEqualTo(parser.parse("kind == 'message' && keyType == 'long' && userId == '10'"))
        Assertions.assertThat(found.map { it.key.from }).containsOnly(10L)
        Assertions.assertThat(found.map { it.key.id }).containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    fun `global recall builds the global filter and returns both topics`() {
        val found = hits(service.recallGlobal(GlobalRecallRequest("apple banana")))

        Assertions.assertThat(store.lastFilter)
            .isEqualTo(parser.parse("kind == 'message' && keyType == 'long'"))
        // All four seeds pass the global filter with the accept-all threshold.
        Assertions.assertThat(found.map { it.key.id }).containsExactlyInAnyOrder(1L, 2L, 3L, 4L)
    }

    @Test
    fun `hits carry the message key and score only`() {
        val found = hits(service.recallInTopic(TopicRecallRequest(200L, "apple banana")))

        Assertions.assertThat(found).hasSize(1)
        val hit = found.single()
        Assertions.assertThat(hit.key.id).isEqualTo(4L)
        Assertions.assertThat(hit.key.from).isEqualTo(20L)
        Assertions.assertThat(hit.key.dest).isEqualTo(200L)
        Assertions.assertThat(hit.score).isNotNull
    }

    @Test
    fun `high threshold filters weak matches`() {
        val found = hits(service.recallInTopic(TopicRecallRequest(100L, "apple banana", threshold = 0.9)))

        Assertions.assertThat(store.lastThreshold).isEqualTo(0.9)
        Assertions.assertThat(found.map { it.key.id }).contains(1L)
        Assertions.assertThat(found.map { it.key.id }).doesNotContain(3L)
    }

    @Test
    fun `limit is passed as topK`() {
        service.recallGlobal(GlobalRecallRequest("apple banana", limit = 3)).blockLast()

        Assertions.assertThat(store.lastTopK).isEqualTo(3)
    }

    @Test
    fun `invalid request fails with InvalidRecallRequestException`() {
        StepVerifier.create(service.recallInTopic(TopicRecallRequest(100L, "  ")))
            .expectError(InvalidRecallRequestException::class.java)
            .verify()
        StepVerifier.create(service.recallGlobal(GlobalRecallRequest("q", limit = 51)))
            .expectError(InvalidRecallRequestException::class.java)
            .verify()
    }

    @Test
    fun `search runs on bounded elastic`() {
        service.recallGlobal(GlobalRecallRequest("apple banana")).blockLast()

        Assertions.assertThat(store.lastSearchThread).startsWith("boundedElastic")
    }
}
