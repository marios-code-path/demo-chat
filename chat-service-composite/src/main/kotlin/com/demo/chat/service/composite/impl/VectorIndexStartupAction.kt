package com.demo.chat.service.composite.impl

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.service.vector.JobTopicNames
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.VectorCoveragePolicy
import com.demo.chat.service.vector.VectorIndexJobStore
import com.demo.chat.service.vector.VectorIndexState
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * What one process does with the durable jobs when it starts.
 *
 * The four steps run in one order, and the order is the rule:
 *
 * 1. Release the stale running jobs of earlier incarnations.
 * 2. Select the covering job.
 * 3. Adopt that job as the in-process target.
 * 4. Start a rebuild, when the operator asked for one.
 *
 * **Steps 3 and 4 must not race.** A rebuild that finishes before coverage
 * discovery installs its own job. A late discovery would then replace that new
 * job with an older one, and the index would report coverage from a job that no
 * longer describes it.
 *
 * The sweep runs first, because a released job must not reach the policy as a
 * running one.
 */
class VectorIndexStartupAction<T>(
    private val jobStore: VectorIndexJobStore<T>,
    private val policy: VectorCoveragePolicy<T>,
    private val state: VectorIndexState<T>,
    private val reindex: MessageReindexService<T>,
    private val nodeId: Int,
    private val keyType: String,
    private val incarnationId: String,
    private val startRebuild: Boolean,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun run(): Mono<Void> =
        releaseStaleJobs()
            .then(adoptCoverage())
            .then(Mono.defer { startRebuildIfAsked() })

    /**
     * Marks the running jobs of earlier incarnations as released.
     *
     * The sweep is best effort, which the design states. A durable job is
     * evidence and never a lock, so a job left running blocks nothing. Every
     * failure therefore logs and the startup continues.
     *
     * A failed listing skips the whole sweep. A failed read or write skips one
     * job and the sweep continues with the rest.
     *
     * A running job of this incarnation is never released. No rebuild of this
     * process has started yet, so such a job would be another live process on
     * one node id, and the node claim lease already refuses that.
     */
    private fun releaseStaleJobs(): Mono<Void> =
        jobStore.listJobTopics()
            .filter { topic -> JobTopicNames.matches(topic.data, nodeId, keyType) }
            .concatMap { topic -> releaseOne(topic.key) }
            .onErrorResume { error ->
                logger.error("Vector index could not list its job topics at startup", error)
                Flux.empty()
            }
            .then()

    private fun releaseOne(jobKey: Key<T>): Mono<Void> =
        jobStore.readJob(jobKey)
            .filter { job -> job.outcome == JobOutcome.RUNNING && job.incarnationId != incarnationId }
            // finishJob applies a terminal outcome and never lowers the stored
            // invalidation fields. A plain write would drop an invalidation that
            // the crashed process recorded.
            .flatMap { job -> jobStore.finishJob(released(job)) }
            .onErrorResume { error ->
                logger.error("Vector index could not release the stale job {}", jobKey, error)
                Mono.empty()
            }

    private fun released(job: IndexJob<T>): IndexJob<T> = job.copy(
        outcome = JobOutcome.RELEASED,
        failureSummary = job.failureSummary
            ?: "An earlier incarnation left this job running.",
    )

    private fun adoptCoverage(): Mono<Void> =
        policy.selectCoveringJob()
            .doOnNext { job ->
                logger.info("Vector index coverage comes from job {}", job.key)
                state.adoptCoveringJob(job.key)
            }
            .then()

    private fun startRebuildIfAsked(): Mono<Void> =
        if (startRebuild) {
            reindex.start().then()
        } else {
            Mono.empty()
        }
}
