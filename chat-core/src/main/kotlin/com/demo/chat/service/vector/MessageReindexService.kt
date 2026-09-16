package com.demo.chat.service.vector

import reactor.core.publisher.Mono

interface MessageReindexService<T> {
    /**
     * Starts one rebuild and returns at once.
     *
     * The answer states whether this call started a run. It never promises the
     * job key, because the run creates its job after the claim and on another
     * scheduler.
     */
    fun start(): Mono<VectorIndexTriggerResult<T>>

    fun status(): VectorIndexStatus<T>
}
