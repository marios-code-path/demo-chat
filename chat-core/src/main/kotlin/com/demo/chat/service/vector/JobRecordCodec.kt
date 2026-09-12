package com.demo.chat.service.vector

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.JobRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Encodes one job record as a versioned JSON string.
 *
 * Every current deployment binds message data to String, so a record travels as
 * text. The version is written first so a later reader can refuse a shape it
 * does not know, rather than bind it wrongly.
 */
class JobRecordCodec(private val mapper: ObjectMapper) {

    fun encode(record: JobRecord<*>): String {
        val node = mapper.valueToTree<ObjectNode>(record)
        node.put(VERSION_FIELD, VERSION)
        return mapper.writeValueAsString(node)
    }

    fun <T> decode(text: String): JobRecord<T> {
        val node = mapper.readTree(text)
        val version = node.get(VERSION_FIELD)?.asInt()

        if (version != VERSION) {
            throw ChatException(
                "A job record of version '$version' cannot be read. This reader knows version $VERSION."
            )
        }

        @Suppress("UNCHECKED_CAST")
        return mapper.treeToValue(node, JobRecord::class.java) as JobRecord<T>
    }

    companion object {
        const val VERSION_FIELD = "version"
        const val VERSION = 1
    }
}
