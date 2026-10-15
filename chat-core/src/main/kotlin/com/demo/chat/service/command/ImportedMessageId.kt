package com.demo.chat.service.command

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.TypeUtil
import java.util.UUID

/** Derives one message id from a room-scoped source id. */
object ImportedMessageId {
    fun derive(sourceId: String, topicId: UUID): UUID {
        if (!sourceId.startsWith("$topicId:")) {
            throw ChatException("The source id '$sourceId' must start with the room id '$topicId:'.")
        }
        return UUID.nameUUIDFromBytes(sourceId.encodeToByteArray())
    }

    fun <T> deriveFor(typeUtil: TypeUtil<T>, dest: T, sourceId: String): T {
        val probe = typeUtil.empty()
        if (probe !is UUID) {
            val typeName = (probe as Any?)?.javaClass?.simpleName ?: "unknown"
            throw ChatException("The key type $typeName cannot hold an imported id from name-based UUID derivation.")
        }
        val topicId = dest as? UUID
            ?: throw ChatException("The room id must be a UUID for imported id derivation.")
        return typeUtil.exactFrom(derive(sourceId, topicId))
    }
}
