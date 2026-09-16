package com.demo.chat.test.vector

import com.demo.chat.domain.ChatException
import com.demo.chat.service.vector.VectorWriteMode
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

/**
 * The write mode of each vector provider.
 *
 * Only the embedded provider refuses a repeated document id. A removal before
 * every write would be wasted work on the simple provider, and
 * `RedisVectorStore` logs an error when a delete removes no document, so every
 * new message would log one.
 */
class VectorWriteModeTests {

    @Test
    fun `the embedded provider removes before it writes`() {
        Assertions
            .assertThat(VectorWriteMode.forVectorSelector("embedded"))
            .isEqualTo(VectorWriteMode.DELETE_THEN_ADD)
    }

    @Test
    fun `the simple provider writes once`() {
        Assertions
            .assertThat(VectorWriteMode.forVectorSelector("simple"))
            .isEqualTo(VectorWriteMode.UPSERT)
    }

    @Test
    fun `the redis provider writes once`() {
        Assertions
            .assertThat(VectorWriteMode.forVectorSelector("redis"))
            .isEqualTo(VectorWriteMode.UPSERT)
    }

    @Test
    fun `the mock provider writes once`() {
        Assertions
            .assertThat(VectorWriteMode.forVectorSelector("mock"))
            .isEqualTo(VectorWriteMode.UPSERT)
    }

    // A default would give a new provider a write policy with no decision, and
    // the wrong policy is silent on three of the four current providers. A new
    // provider must state its own answer here.
    @Test
    fun `an unknown selector has no write mode`() {
        Assertions
            .assertThatThrownBy { VectorWriteMode.forVectorSelector("pinecone") }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("pinecone")
            .hasMessageContaining("embedded, simple, redis, and mock")
    }

    // The four legal pairs of VectorSelectorValidation name these four vector
    // values. Every one of them must answer, or a legal deployment cannot
    // build its indexer.
    @Test
    fun `every legal vector selector has a write mode`() {
        listOf("embedded", "simple", "redis", "mock").forEach { selector ->
            Assertions
                .assertThat(VectorWriteMode.forVectorSelector(selector))
                .`as`(selector)
                .isNotNull()
        }
    }
}
