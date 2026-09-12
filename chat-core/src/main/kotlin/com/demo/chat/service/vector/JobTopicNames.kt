package com.demo.chat.service.vector

import java.time.Instant

/**
 * Names of the system topics that hold rebuild job records.
 *
 * The prefix is the only discriminator a topic carries. `MessageTopic` holds a
 * key and a name and nothing else, so no owner field and no root key can mark a
 * topic as a system topic.
 */
object JobTopicNames {
    const val PREFIX = "__vectorjob__"

    private const val SEPARATOR = ":"

    fun nameFor(nodeId: Int, keyType: String, startedAt: Instant, incarnationId: String): String =
        listOf(PREFIX, nodeId.toString(), keyType, startedAt.toEpochMilli().toString(), incarnationId)
            .joinToString(SEPARATOR)

    fun isJobTopic(name: String): Boolean = name.startsWith(PREFIX)

    fun matches(name: String, nodeId: Int, keyType: String): Boolean =
        isJobTopic(name) &&
            name.split(SEPARATOR).let { parts ->
                parts.size >= 3 && parts[1] == nodeId.toString() && parts[2] == keyType
            }
}
