package com.demo.chat.mcp.client

/**
 * One topic that the adapter read from a deployment.
 *
 * Both ids are exact text. A Long id above 2^53 keeps every digit, because no
 * value here ever passes through a floating point type.
 *
 * The root is the id of the domain root key. It comes from the response. The
 * adapter never derives one.
 */
data class Topic(
    /** The id of the topic key, as canonical text. */
    val id: String,
    /** The id of the domain root key, as canonical text. */
    val root: String,
    /** The topic name. */
    val data: String,
)
