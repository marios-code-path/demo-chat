package com.demo.chat.service.vector

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.IndexJob
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Reads one stored job back from any backend shape.
 *
 * The memory store returns the object it was given. Redis returns a map after
 * its JSON round trip. Cassandra returns the JSON string it stored.
 */
class IndexJobCodec<T>(private val mapper: ObjectMapper) {

    @Suppress("UNCHECKED_CAST")
    fun decode(data: Any): IndexJob<T> = when (data) {
        is IndexJob<*> -> data as IndexJob<T>
        is Map<*, *> -> mapper.convertValue(data, IndexJob::class.java) as IndexJob<T>
        is String -> mapper.readValue(data, IndexJob::class.java) as IndexJob<T>
        else -> throw ChatException(
            "A stored index job cannot be read from '${data.javaClass.name}'."
        )
    }
}
