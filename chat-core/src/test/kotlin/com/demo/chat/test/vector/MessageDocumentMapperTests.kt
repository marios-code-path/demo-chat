package com.demo.chat.test.vector

import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.service.vector.MessageDocumentMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class MessageDocumentMapperTests {

    private val mapper = MessageDocumentMapper<Long>(LongUtil(), "long")

    private val message: Message<Long, String> =
        Message.create(MessageKey.create(10L, 20L, 30L), "hello apple", true)

    @Test
    fun `message becomes one document with id text and metadata`() {
        val doc = mapper.toDocument(message)

        assertThat(doc.id).isEqualTo("message:long:10")
        assertThat(doc.text).isEqualTo("hello apple")
        assertThat(doc.metadata).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "kind" to "message",
                "messageId" to "10",
                "topicId" to "30",
                "userId" to "20",
                "keyType" to "long",
            )
        )
    }

    @Test
    fun `document id uses key type and message id`() {
        assertThat(mapper.documentId(42L)).isEqualTo("message:long:42")
    }

    @Test
    fun `uuid keys stringify in id and metadata`() {
        val uuidMapper = MessageDocumentMapper<UUID>(UUIDUtil(), "uuid")
        val messageId = UUID.randomUUID()
        val doc = uuidMapper.toDocument(
            Message.create(
                MessageKey.create(messageId, UUID.randomUUID(), UUID.randomUUID()),
                "hi",
                true,
            )
        )

        assertThat(doc.id).isEqualTo("message:uuid:$messageId")
        assertThat(doc.metadata["messageId"]).isEqualTo(messageId.toString())
        assertThat(doc.metadata["keyType"]).isEqualTo("uuid")
    }
}