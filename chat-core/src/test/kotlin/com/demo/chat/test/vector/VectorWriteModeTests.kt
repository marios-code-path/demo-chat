package com.demo.chat.test.vector

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
}
