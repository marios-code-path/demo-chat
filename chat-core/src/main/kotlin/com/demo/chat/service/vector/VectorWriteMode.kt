package com.demo.chat.service.vector

import com.demo.chat.domain.ChatException

/**
 * How the indexer writes a document that may already exist.
 *
 * The document id comes from the message id, so a rebuild always meets an id
 * it wrote before. The providers disagree about what a repeat means, so the
 * write mode follows the provider.
 */
enum class VectorWriteMode {
    /**
     * One add call. The store overwrites a document of the same id.
     *
     * The mock, simple, and redis providers all behave this way. A removal
     * before the write would be wasted work on the first two. On redis it
     * would also be noisy: `RedisVectorStore` logs an error when a delete
     * removes no document, so every new message would log one.
     */
    UPSERT,

    /**
     * A remove call, then an add call.
     *
     * The embedded provider refuses a repeated id with
     * `IllegalArgumentException: Duplicate id`, so the old document must go
     * first. The pair is not atomic.
     */
    DELETE_THEN_ADD;

    companion object {
        /**
         * The mode that one vector selector needs.
         *
         * Every current selector is named here, and every other value is
         * refused. A default would give a new provider a write policy without
         * a decision, and the wrong policy is silent on three of the four
         * providers. A new provider must state its own answer.
         */
        fun forVectorSelector(selector: String): VectorWriteMode =
            when (selector.lowercase()) {
                "embedded" -> DELETE_THEN_ADD
                "simple" -> UPSERT
                "redis" -> UPSERT
                "mock" -> UPSERT
                else -> throw ChatException(
                    "app.service.core.vector=$selector has no vector write mode. " +
                        "The known values are embedded, simple, redis, and mock."
                )
            }
    }
}
