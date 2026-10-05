package com.demo.chat.test.index.cassandra

import com.demo.chat.config.index.cassandra.IndexServiceConfiguration
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The message index tables require a message id of the key type. See `CHAT-xcmpudyb`. */
class IndexShapeTypesTests {

    @Test
    fun `a long store requires a bigint message id in every message index table`() {
        assertThat(IndexServiceConfiguration.indexTypes(TypeUtil.LongUtil)).isEqualTo(
            mapOf(
                "chat_message_user" to mapOf("msg_id" to "bigint"),
                "chat_message_topic" to mapOf("msg_id" to "bigint"),
                "chat_message_index_by_id" to mapOf("msg_id" to "bigint"),
            )
        )
    }

    @Test
    fun `a uuid store requires a timeuuid message id`() {
        assertThat(IndexServiceConfiguration.idType(UUIDUtil())).isEqualTo("timeuuid")
    }
}
