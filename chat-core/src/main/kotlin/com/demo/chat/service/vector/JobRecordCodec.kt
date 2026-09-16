package com.demo.chat.service.vector

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.JobRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Encodes one job record as a versioned JSON envelope.
 *
 * Every current deployment binds message data to String, so a record travels as
 * text.
 *
 * The version sits beside the record, not inside it. A version written among
 * the record fields reaches the binder as an unknown property, and the mapper
 * the deployments use refuses one. Field order gives no protection from that,
 * because a binder reads a whole object.
 */
class JobRecordCodec(private val mapper: ObjectMapper) {

    fun encode(record: JobRecord<*>): String {
        val envelope = mapper.createObjectNode()
        envelope.put(VERSION_FIELD, VERSION)
        envelope.set<ObjectNode>(RECORD_FIELD, mapper.valueToTree(record))
        return mapper.writeValueAsString(envelope)
    }

    /**
     * Reads the version before it binds anything. An unknown version stops the
     * read there, so a payload this reader cannot understand is never bound.
     */
    fun <T> decode(text: String): JobRecord<T> {
        val envelope = mapper.readTree(text)
        val version = envelope.get(VERSION_FIELD)?.asInt()

        if (version != VERSION) {
            throw ChatException(
                "A job record of version '$version' cannot be read. This reader knows version $VERSION."
            )
        }

        val payload = envelope.get(RECORD_FIELD)
            ?: throw ChatException("A job record envelope carries no '$RECORD_FIELD' field.")

        @Suppress("UNCHECKED_CAST")
        return mapper.treeToValue(payload, JobRecord::class.java) as JobRecord<T>
    }

    companion object {
        const val VERSION_FIELD = "version"
        const val RECORD_FIELD = "record"
        const val VERSION = 1
    }
}
