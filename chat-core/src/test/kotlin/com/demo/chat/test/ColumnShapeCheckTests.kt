package com.demo.chat.test

import com.demo.chat.service.core.ColumnShapeCheck
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** The shared shape check names each missing table and column. See `CHAT-avduuqwp`, T7. */
class ColumnShapeCheckTests {
    private val required = mapOf("auth_metadata" to setOf("principal_root", "target_root"), "keys" to setOf("id", "root"))

    @Test
    fun `a store with every element passes`() {
        val have = mapOf("auth_metadata" to setOf("id", "principal_root", "target_root"), "keys" to setOf("id", "root"))
        assertThatCode { ColumnShapeCheck("Keyspace k", required) { have }.check() }.doesNotThrowAnyException()
    }

    @Test
    fun `a missing column and a missing table are both named`() {
        val have = mapOf("auth_metadata" to setOf("id", "target_root"))
        assertThatThrownBy { ColumnShapeCheck("Keyspace k", required) { have }.check() }
            .hasMessageContaining("Keyspace k")
            .hasMessageContaining("auth_metadata.principal_root")
            .hasMessageContaining("keys")
            .hasMessageContaining("Recreate the store")
    }
}
