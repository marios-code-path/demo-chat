package com.demo.chat.service.vector

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

interface VectorIndexJobStore<T> {
    /**
     * Creates the job topic and writes the RUNNING job.
     *
     * The topic is written through topic persistence, the topic index, and
     * `pubsub.open()`. It never goes through `addRoom()`, which rejects the
     * reserved prefix for every caller. `pubsub.open()` is required, because
     * the memory backend answers `sendMessage()` with `Object not Found` for a
     * topic it never opened.
     */
    fun createJob(startedAt: Instant): Mono<IndexJob<T>>

    fun write(job: IndexJob<T>): Mono<Void>

    /**
     * Applies a terminal outcome and its counts.
     *
     * It never lowers the stored invalidation fields. An invalidation that
     * lands between the state decision and this write must survive it,
     * otherwise a restart under `stored` would trust a job that a live failure
     * already invalidated.
     *
     * This call and [invalidate] run in order on one worker, so no pair of
     * writes interleaves inside a read and a write.
     */
    fun finishJob(job: IndexJob<T>): Mono<Void>

    /**
     * Reads the job stored under [jobKey]. A missing job answers empty. A
     * stored job whose key differs fails with `JobLookupException`.
     */
    fun readJob(jobKey: Key<T>): Mono<IndexJob<T>>

    /**
     * Finds the one job whose topic is [topicKey], through the key-value index
     * field `topicId`. Every fault fails with `JobLookupException`, and none
     * answers empty. See `CHAT-avduuqwp`, D3.
     */
    fun readJobByTopic(topicKey: Key<T>): Mono<IndexJob<T>>

    /** One listing. The caller uses it for discovery and for scan exclusion. */
    fun listJobTopics(): Flux<out MessageTopic<T>>

    fun invalidate(jobKey: Key<T>, at: Instant): Mono<Void>
}
