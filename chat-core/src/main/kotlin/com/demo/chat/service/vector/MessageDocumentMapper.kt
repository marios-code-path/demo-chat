package com.demo.chat.service.vector

import com.demo.chat.domain.Message
import com.demo.chat.domain.TypeUtil
import org.springframework.ai.document.Document

/**
 * Maps one persisted message to the Spring AI document that enters the
 * recall corpus. The document id and the metadata values are the recall
 * contract; the filter expressions in MessageRecallServiceImpl depend on
 * them.
 */
class MessageDocumentMapper<T>(
    private val typeUtil: TypeUtil<T>,
    private val keyType: String,
) {

    fun toDocument(message: Message<T, String>): Document =
        Document.builder()
            .id(documentId(message.key.id))
            .text(message.data)
            .metadata(
                mapOf(
                    "kind" to "message",
                    "messageId" to typeUtil.toString(message.key.id),
                    "topicId" to typeUtil.toString(message.key.dest),
                    "userId" to typeUtil.toString(message.key.from),
                    "keyType" to keyType,
                )
            )
            .build()

    fun documentId(messageId: T): String = "message:$keyType:${typeUtil.toString(messageId)}"
}