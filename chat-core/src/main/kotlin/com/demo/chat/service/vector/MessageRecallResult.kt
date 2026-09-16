package com.demo.chat.service.vector

/**
 * One recall answer.
 *
 * [hits] is bounded by the request limit. `RequestResponse.kt` caps that limit
 * at 50, so the list is bounded by design.
 *
 * [indexComplete] reports the coverage at the time of the read. An empty list
 * has two meanings, and only this flag separates them.
 */
data class MessageRecallResult<T>(
    val indexComplete: Boolean,
    val hits: List<MessageRecallHit<T>>,
)
