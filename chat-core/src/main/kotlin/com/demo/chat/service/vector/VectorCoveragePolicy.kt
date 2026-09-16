package com.demo.chat.service.vector

import com.demo.chat.domain.IndexJob
import reactor.core.publisher.Mono

enum class VectorTrust {
    /** Only a successful job of this incarnation counts. */
    NONE,

    /**
     * A successful job of this node and key type counts, whatever incarnation
     * wrote it.
     *
     * This is an operator assertion. The vector store and the key-value store
     * must survive the same restart, and `VectorStore` cannot count documents,
     * so no read can check it.
     */
    STORED,
}

fun interface VectorCoveragePolicy<T> {
    /** Empty when no job covers the index. */
    fun selectCoveringJob(): Mono<IndexJob<T>>
}
