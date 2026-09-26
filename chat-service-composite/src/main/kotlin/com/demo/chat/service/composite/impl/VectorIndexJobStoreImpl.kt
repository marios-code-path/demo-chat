package com.demo.chat.service.composite.impl

import com.demo.chat.domain.EmbeddingIdentity
import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.core.KeyValueIndexService
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.TopicPersistence
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.service.vector.IndexJobCodec
import com.demo.chat.service.vector.JobLookupException
import com.demo.chat.service.vector.JobLookupFault
import com.demo.chat.service.vector.JobTopicNames
import com.demo.chat.service.vector.VectorIndexJobStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

/**
 * The durable job record of the vector index. See `CHAT-avduuqwp`, D2 and D3.
 *
 * A job has two keys. The job key is a KEY_VALUE_PAIR key, and the job is
 * stored under it. The topic key is a separate MESSAGE_TOPIC key, and the
 * progress records go to that topic. The key-value index holds the field
 * [TOPIC_ID] of each job, so a reader finds a job from its topic.
 */
class VectorIndexJobStoreImpl<T : Any, V, Q>(
    private val topicPersistence: TopicPersistence<T>,
    private val topicIndex: TopicIndexService<T, Q>,
    private val pubsub: TopicPubSubService<T, V>,
    private val keyValueStore: KeyValueStore<T, Any>,
    private val keyValueIndex: KeyValueIndexService<T, Q>,
    private val topicIdQuery: (String) -> Q,
    private val codec: IndexJobCodec<T>,
    private val nodeId: Int,
    private val keyType: String,
    private val embeddingIdentity: EmbeddingIdentity,
    private val incarnationId: String,
    private val workerKey: Mono<out Key<T>>,
) : VectorIndexJobStore<T> {

    /**
     * The writes run in a fixed order, and none is atomic with another. A
     * failed step emits its error and starts no later step. It does not undo
     * the earlier steps. The index write starts only after the job write
     * reports success.
     */
    override fun createJob(startedAt: Instant): Mono<IndexJob<T>> =
        Mono.zip(keyValueStore.key(), topicPersistence.key(), workerKey)
            .flatMap { keys ->
                val jobKey: Key<T> = keys.t1
                val topicKey: Key<T> = keys.t2
                val topic = MessageTopic.create(
                    topicKey,
                    JobTopicNames.nameFor(nodeId, keyType, startedAt, incarnationId)
                )
                val job = IndexJob(
                    key = jobKey,
                    topicKey = topicKey,
                    nodeId = nodeId,
                    keyType = keyType,
                    embeddingIdentity = embeddingIdentity.value,
                    incarnationId = incarnationId,
                    startedBy = keys.t3,
                    startedAt = startedAt,
                )
                topicPersistence.add(topic)
                    .then(topicIndex.add(topic))
                    // A memory topic that was never opened answers sendMessage
                    // with Object not Found.
                    .then(pubsub.open(topicKey.id))
                    .then(write(job))
                    .then(keyValueIndex.add(KeyValuePair.create(job.key, job as Any)))
                    .thenReturn(job)
            }

    override fun write(job: IndexJob<T>): Mono<Void> =
        keyValueStore.add(KeyValuePair.create(job.key, job as Any))

    /**
     * Reads the job stored under [jobKey]. A missing job answers empty.
     *
     * The stored value must hold the key it is stored under. The two are
     * separate facts, and only this read can compare them. A record under key
     * A that holds key B would make every later caller act on B.
     */
    override fun readJob(jobKey: Key<T>): Mono<IndexJob<T>> =
        keyValueStore.get(jobKey)
            .map { pair -> codec.decode(pair.data) }
            .flatMap { job ->
                if (job.key == jobKey) Mono.just(job)
                else Mono.error(
                    JobLookupException(
                        JobLookupFault.STORED_KEY_MISMATCH,
                        "The job stored under key '$jobKey' holds key '${job.key}'.",
                    )
                )
            }

    /** Exactly one index entry must name [topicKey]. Every fault is an error. */
    override fun readJobByTopic(topicKey: Key<T>): Mono<IndexJob<T>> =
        keyValueIndex.findBy(topicIdQuery(topicKey.id.toString()))
            .collectList()
            .flatMap { matches ->
                when (matches.size) {
                    0 -> Mono.error(JobLookupException(JobLookupFault.MISSING, "No job names the topic '$topicKey'."))
                    1 -> readJob(matches.single())
                        .switchIfEmpty(Mono.error {
                            JobLookupException(JobLookupFault.DANGLING, "The index names job '${matches.single()}', and the store holds none.")
                        })
                    else -> Mono.error(JobLookupException(JobLookupFault.DUPLICATE, "${matches.size} jobs name the topic '$topicKey'."))
                }
            }
            .flatMap { job ->
                // Key equality reads the root, so a reference with another root does not match.
                if (job.topicKey == topicKey) Mono.just(job)
                else Mono.error(
                    JobLookupException(JobLookupFault.TOPIC_MISMATCH, "The job '${job.key}' names topic '${job.topicKey}', not '$topicKey'.")
                )
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

    companion object {
        /** The key-value index field that names the topic of a job. */
        const val TOPIC_ID = "topicId"
    }
}
