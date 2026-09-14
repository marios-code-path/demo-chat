package com.demo.chat.service.composite.impl

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.EmbeddingIdentity
import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.TopicPersistence
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.service.vector.IndexJobCodec
import com.demo.chat.service.vector.JobTopicNames
import com.demo.chat.service.vector.VectorIndexJobStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

class VectorIndexJobStoreImpl<T, V, Q>(
    private val topicPersistence: TopicPersistence<T>,
    private val topicIndex: TopicIndexService<T, Q>,
    private val pubsub: TopicPubSubService<T, V>,
    private val keyValueStore: KeyValueStore<T, Any>,
    private val codec: IndexJobCodec<T>,
    private val nodeId: Int,
    private val keyType: String,
    private val embeddingIdentity: EmbeddingIdentity,
    private val incarnationId: String,
    private val workerKey: Key<T>,
) : VectorIndexJobStore<T> {

    override fun createJob(startedAt: Instant): Mono<IndexJob<T>> =
        topicPersistence
            .key()
            .flatMap { key ->
                val topic = MessageTopic.create(
                    key,
                    JobTopicNames.nameFor(nodeId, keyType, startedAt, incarnationId)
                )
                val job = IndexJob(
                    key = key,
                    nodeId = nodeId,
                    keyType = keyType,
                    embeddingIdentity = embeddingIdentity.value,
                    incarnationId = incarnationId,
                    startedBy = workerKey,
                    startedAt = startedAt,
                )
                topicPersistence.add(topic)
                    .then(topicIndex.add(topic))
                    // A memory topic that was never opened answers sendMessage
                    // with Object not Found.
                    .then(pubsub.open(key.id))
                    .then(write(job))
                    .thenReturn(job)
            }

    override fun write(job: IndexJob<T>): Mono<Void> =
        keyValueStore.add(KeyValuePair.create(job.key, job as Any))

    /**
     * Reads the job that [topicKey] names.
     *
     * The stored value must hold the key it is stored under. The two are
     * separate facts, and only this read can compare them. A record under key
     * A that holds key B makes every later caller act on B. The coverage
     * policy adopts B as its invalidation target, key A stays clean, and key A
     * can cover the index again after a restart.
     */
    override fun readJob(topicKey: Key<T>): Mono<IndexJob<T>> =
        keyValueStore.get(topicKey)
            .map { pair -> codec.decode(pair.data) }
            .flatMap { job ->
                if (job.key == topicKey) {
                    Mono.just(job)
                } else {
                    Mono.error(
                        ChatException(
                            "A job stored under key '$topicKey' holds root key '${job.key}'."
                        )
                    )
                }
            }

    /**
     * Every reserved topic, from every node.
     *
     * The scan exclusion needs all of them. Another node's job records sit in
     * the same message store, and a listing narrowed to this node would let
     * them into vector recall. A caller that wants this node's own jobs, such
     * as the coverage policy, narrows the result itself.
     */
    override fun listJobTopics(): Flux<out MessageTopic<T>> =
        topicPersistence.all()
            .filter { topic -> JobTopicNames.isJobTopic(topic.data) }

    // Every read and write pair below runs to completion before the next one
    // starts. Two callers that both read, change, and write one job would
    // otherwise lose one of the changes.
    //
    // subscribeOn does not give this. It moves the subscription to another
    // thread and returns. An asynchronous backend yields between its read and
    // its write, so a second operation interleaves there. concatMap waits for
    // each inner publisher to complete, which is the property this needs.
    private val writer = SerialWriter()

    override fun finishJob(job: IndexJob<T>): Mono<Void> =
        writer.submit(
            readJob(job.key)
                .map { stored ->
                    job.copy(
                        invalidationCount = maxOf(job.invalidationCount, stored.invalidationCount),
                        lastInvalidationAt = stored.lastInvalidationAt ?: job.lastInvalidationAt,
                    )
                }
                .defaultIfEmpty(job)
                .flatMap { merged -> write(merged) }
        )

    override fun invalidate(jobKey: Key<T>, at: Instant): Mono<Void> =
        writer.submit(
            readJob(jobKey)
                .flatMap { job ->
                    write(job.copy(invalidationCount = job.invalidationCount + 1, lastInvalidationAt = at))
                }
        )

    /**
     * The configuration registers this as the bean destroy method.
     *
     * `block()` takes no timeout. `close()` is already bounded, and an outer
     * timeout would cancel it, which would dispose the worker while work is
     * still queued.
     */
    fun close() {
        writer.close().block()
    }
}
