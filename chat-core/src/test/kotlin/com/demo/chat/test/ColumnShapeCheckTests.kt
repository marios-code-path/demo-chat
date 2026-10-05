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
        val have = mapOf(
            "auth_metadata" to mapOf("id" to "bigint", "principal_root" to "bigint", "target_root" to "bigint"),
            "keys" to mapOf("id" to "bigint", "root" to "bigint"),
        )
        assertThatCode { ColumnShapeCheck("Keyspace k", required) { have }.check() }.doesNotThrowAnyException()
    }

    @Test
    fun `a missing column and a missing table are both named`() {
        val have = mapOf("auth_metadata" to mapOf("id" to "bigint", "target_root" to "bigint"))
        assertThatThrownBy { ColumnShapeCheck("Keyspace k", required) { have }.check() }
            .hasMessageContaining("Keyspace k")
            .hasMessageContaining("auth_metadata.principal_root")
            .hasMessageContaining("keys")
            .hasMessageContaining("Recreate the store")
    }

    // A store from an earlier schema can hold every name with another type.
    // It then starts, and fails at the first write. See CHAT-xcmpudyb.
    @Test
    fun `a column of the wrong type is named with both types`() {
        val types = mapOf("chat_message_id" to mapOf("msg_id" to "bigint"))
        val have = mapOf("chat_message_id" to mapOf("msg_id" to "timestamp"))

        assertThatThrownBy { ColumnShapeCheck("Keyspace k", emptyMap(), types) { have }.check() }
            .hasMessageContaining("Keyspace k")
            .hasMessageContaining("chat_message_id.msg_id is timestamp, required bigint")
            .hasMessageContaining("Recreate the store")
    }

    @Test
    fun `a column of the required type passes`() {
        val types = mapOf("chat_message_id" to mapOf("msg_id" to "bigint"))
        val have = mapOf("chat_message_id" to mapOf("msg_id" to "bigint"))

        assertThatCode { ColumnShapeCheck("Keyspace k", emptyMap(), types) { have }.check() }.doesNotThrowAnyException()
    }

    // A typed column must also exist. The type rule alone reports it as missing.
    @Test
    fun `a typed column that is absent is named as missing`() {
        val types = mapOf("chat_message_id" to mapOf("msg_id" to "bigint"))

        assertThatThrownBy { ColumnShapeCheck("Keyspace k", emptyMap(), types) { emptyMap() }.check() }
            .hasMessageContaining("Missing: chat_message_id")
    }
}
