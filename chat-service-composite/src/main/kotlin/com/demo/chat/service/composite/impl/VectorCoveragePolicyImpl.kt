package com.demo.chat.service.composite.impl

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.EmbeddingIdentity
import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.service.vector.JobTopicNames
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.vector.VectorCoveragePolicy
import com.demo.chat.service.vector.VectorIndexJobStore
import com.demo.chat.service.vector.VectorTrust
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

/**
 * Selects the newest applicable successful job.
 *
 * The policy never compares a stored value with the process generation. The
 * generation is a process-local race token and restarts at zero, so a stored
 * value and a fresh counter can match by accident.
 *
 * The policy never falls back to an older successful job. An invalidated newest
 * job means the index lost coverage, and an older job cannot restore it.
 */
class VectorCoveragePolicyImpl<T>(
    private val jobStore: VectorIndexJobStore<T>,
    private val trust: VectorTrust,
    private val incarnationId: String,
    private val nodeId: Int,
    private val keyType: String,
    private val embeddingIdentity: EmbeddingIdentity,
    private val typeUtil: TypeUtil<T>,
) : VectorCoveragePolicy<T> {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun selectCoveringJob(): Mono<IndexJob<T>> =
        jobStore.listJobTopics()
            // The name decides which topics to read. The listing carries every
            // reserved topic, from every node, because the scan exclusion
            // needs all of them. Narrowing here rather than after the read
            // means this node never reads another node's job at all.
            .filter { topic -> JobTopicNames.matches(topic.data, nodeId, keyType) }
            .flatMap { topic -> jobStore.readJobByTopic(topic.key) }
            // A name and a record that disagree fail the read rather than
            // dropping that job. Dropping it would expose an older clean job,
            // and the spec requires the read to fail closed.
            //
            // A lookup fault fails the selection. The policy never skips a
            // broken topic, because that would expose an older job. See
            // CHAT-avduuqwp, D3.
            .flatMap { job ->
                if (job.nodeId == nodeId && job.keyType == keyType) {
                    Mono.just(job)
                } else {
                    Mono.error(
                        ChatException(
                            "A job topic of node $nodeId and key type '$keyType' holds a record " +
                                "of node ${job.nodeId} and key type '${job.keyType}'."
                        )
                    )
                }
            }
            // This filter runs before the sort, and that order is the rule.
            // A running or failed repair is the newest job. Dropping it here
            // leaves the older successful job as the newest applicable one,
            // which keeps coverage during a repair. Dropping it after the
            // sort would select the repair and report no coverage.
            .filter { job -> job.outcome == JobOutcome.SUCCEEDED }
            // This filter runs before the sort and before next(), and that
            // order is the rule. A newer job of a foreign identity would
            // otherwise reach next() first. The policy would select it, reject
            // it, and report no coverage. A valid older job of this identity
            // would then be hidden behind it, and the index would rebuild for
            // no reason.
            //
            // A legacy record carries null, which never equals an identity
            // value. So the first start after this change reports an
            // incomplete index and waits for a rebuild. That outcome is
            // correct, because those vectors came from a model that no longer
            // has a name.
            .filter { job -> job.embeddingIdentity == embeddingIdentity.value }
            .filter { job -> trust == VectorTrust.STORED || job.incarnationId == incarnationId }
            // Newest first. Two jobs of one millisecond order by root key, so
            // the choice never depends on which read completed first. The
            // higher key wins. TypeUtil compares the key in its own type,
            // because a text compare puts "9" above "10".
            .sort(
                compareByDescending<IndexJob<T>> { job -> job.startedAt }
                    .thenByDescending(Comparator<T> { a, b -> typeUtil.compare(a, b) }) { job -> job.key.id }
            )
            .next()
            .filter { job -> job.covers }
            .onErrorResume { error ->
                logger.error("Vector coverage read failed", error)
                Mono.empty()
            }
}
