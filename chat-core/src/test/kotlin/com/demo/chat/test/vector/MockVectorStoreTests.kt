package com.demo.chat.test.vector

import com.demo.chat.service.dummy.DummyEmbeddingModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest

class MockVectorStoreTests {

    private val store = MockVectorStore()

    private fun doc(id: String, text: String, topicId: String) =
        Document.builder()
            .id(id)
            .text(text)
            .metadata(
                mapOf(
                    "kind" to "message",
                    "messageId" to id,
                    "topicId" to topicId,
                    "userId" to "1",
                    "keyType" to "long",
                )
            )
            .build()

    @BeforeEach
    fun seed() {
        store.add(
            listOf(
                doc("m1", "apple banana", "3"),
                doc("m2", "apple pie", "3"),
                doc("m3", "zebra stripe", "3"),
                doc("m4", "apple banana cake", "9"),
            )
        )
    }

    @Test
    fun `shared substrings rank first`() {
        val hits = store.similaritySearch(
            SearchRequest.builder().query("apple banana").topK(4).build()
        )

        assertThat(hits.map { it.id }).first().isEqualTo("m1")
        assertThat(hits.map { it.id }).last().isEqualTo("m3")
    }

    @Test
    fun `filter keeps matching topic only`() {
        val hits = store.similaritySearch(
            SearchRequest.builder()
                .query("apple banana")
                .topK(10)
                .filterExpression("kind == 'message' && keyType == 'long' && topicId == '9'")
                .build()
        )

        assertThat(hits.map { it.id }).containsExactly("m4")
    }

    @Test
    fun `high threshold drops weak matches`() {
        val hits = store.similaritySearch(
            SearchRequest.builder()
                .query("apple banana")
                .topK(10)
                .similarityThreshold(0.9)
                .build()
        )

        assertThat(hits.map { it.id }).contains("m1")
        assertThat(hits.map { it.id }).doesNotContain("m3")
    }

    @Test
    fun `accept all threshold returns everything matching the filter`() {
        val hits = store.similaritySearch(
            SearchRequest.builder()
                .query("apple banana")
                .topK(10)
                .similarityThresholdAll()
                .build()
        )

        assertThat(hits).hasSize(4)
    }

    @Test
    fun `delete removes by document id`() {
        store.delete(listOf("m1", "m3"))

        assertThat(store.ids).containsExactlyInAnyOrder("m2", "m4")
    }

    @Test
    fun `dummy embedding is deterministic and dimensional`() {
        val model = DummyEmbeddingModel()
        val first = model.embed("apple banana")
        val second = model.embed("apple banana")
        val other = model.embed("zebra stripe")

        assertThat(first).isEqualTo(second)
        assertThat(first).isNotEqualTo(other)
        assertThat(first).hasSize(256)
        assertThat(model.dimensions()).isEqualTo(256)
    }
}