package com.demo.chat.service.composite.impl

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Message
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.service.vector.VectorIndexClaim
import com.demo.chat.service.vector.VectorIndexJobStore
import com.demo.chat.service.vector.VectorIndexState
import com.demo.chat.service.vector.VectorIndexStatus
import com.demo.chat.service.vector.VectorRebuildReport
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class MessageReindexServiceImpl<T, V>(
    private val persistence: MessagePersistence<T, V>,
    private val indexer: MessageVectorIndexer<T>,
    private val state: VectorIndexState<T>,
    private val jobStore: VectorIndexJobStore<T>,
    private val clock: Clock = Clock.systemUTC(),
    private val scheduler: Scheduler = Schedulers.boundedElastic(),
) : MessageReindexService<T> {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun status(): VectorIndexStatus = state.status()

    override fun start(): Mono<VectorIndexStatus> = Mono.fromSupplier {
        val claim = state.claim()
        if (claim.accepted) {
            val startedAt = clock.instant()
            try {
                rebuild(claim, startedAt)
                    .subscribeOn(scheduler)
                    .subscribe(
                        {},
                        { error -> release(claim, startedAt, error) },
                    )
            } catch (error: Throwable) {
                release(claim, startedAt, error)
            }
        }
        claim.status
    }

    /**
     * Releases a claim that no rebuild can finish. A rejected scheduler and an
     * unexpected terminal error both reach this path. Without the release the
     * phase stays REBUILDING and every later trigger reports busy.
     */
    private fun release(
        claim: VectorIndexClaim,
        startedAt: Instant,
        error: Throwable,
    ) {
        logger.error("Vector reindex terminated unexpectedly", error)
        if (!state.status().running) {
            return
        }
        state.finish(
            claim,
            VectorRebuildReport(startedAt, clock.instant(), 0L, 0L, 0L, 0L),
            summary(error),
            null,
        )
    }

    private fun rebuild(
        claim: VectorIndexClaim,
        startedAt: Instant,
    ): Mono<Void> =
        jobStore.createJob(startedAt)
            .flatMap { job ->
                // One listing serves discovery and exclusion. It runs before
                // the scan, and a failure stops the run, because a scan
                // without the full exclusion set would index job records.
                jobStore.listJobTopics()
                    .map { topic -> topic.key.id }
                    .collectList()
                    .flatMap { topicIds ->
                        // This job writes records to its own topic while the
                        // scan runs, so that topic joins the set explicitly.
                        scanWith(claim, startedAt, job, topicIds.toSet() + job.key.id)
                    }
                    .onErrorResume { error ->
                        logger.error("Vector reindex could not list its job topics", error)
                        finishRun(
                            claim,
                            job,
                            VectorRebuildReport(startedAt, clock.instant(), 0L, 0L, 0L, 0L),
                            summary(error),
                        )
                    }
            }

    private fun scanWith(
        claim: VectorIndexClaim,
        startedAt: Instant,
        job: IndexJob<T>,
        exclusion: Set<T>,
    ): Mono<Void> {
        val attempted = AtomicLong()
        val indexed = AtomicLong()
        val skipped = AtomicLong()
        val failed = AtomicLong()
        val lastFailure = AtomicReference<String?>()

        val scan = Flux.defer { persistence.all() }
            // Job records live in the same message store. The filter runs
            // before every counter, so attempted describes user messages.
            .filter { message -> message.key.dest !in exclusion }
            .concatMap { message ->
                attempted.incrementAndGet()
                if (!message.record) {
                    skipped.incrementAndGet()
                    Mono.empty()
                } else {
                    Mono.defer { indexer.add(asText(message)) }
                        .doOnSuccess { indexed.incrementAndGet() }
                        .onErrorResume { error ->
                            failed.incrementAndGet()
                            lastFailure.set(summary(error))
                            logger.error("Vector reindex message failed", error)
                            Mono.empty()
                        }
                }
            }

        return scan
            .then(
                finish(
                    claim,
                    job,
                    startedAt,
                    attempted,
                    indexed,
                    skipped,
                    failed,
                    lastFailure,
                )
            )
            .onErrorResume { error ->
                lastFailure.set(summary(error))
                logger.error("Vector reindex scan failed", error)
                finish(
                    claim,
                    job,
                    startedAt,
                    attempted,
                    indexed,
                    skipped,
                    failed,
                    lastFailure,
                )
            }
    }

    private fun finish(
        claim: VectorIndexClaim,
        job: IndexJob<T>,
        startedAt: Instant,
        attempted: AtomicLong,
        indexed: AtomicLong,
        skipped: AtomicLong,
        failed: AtomicLong,
        lastFailure: AtomicReference<String?>,
    ): Mono<Void> = Mono.defer {
        val report = VectorRebuildReport(
            startedAt,
            clock.instant(),
            attempted.get(),
            indexed.get(),
            skipped.get(),
            failed.get(),
        )
        finishRun(claim, job, report, lastFailure.get())
    }

    /**
     * The state decides, and the durable outcome follows that decision.
     *
     * A durable SUCCEEDED written before the generation check can be left
     * behind by a live failure that arrives between the two steps. This
     * process would refuse to install that job, and a restart under the stored
     * trust policy would read a clean job and believe it.
     *
     * The verdict describes this run. status.complete describes the index, and
     * an earlier job can still cover it, so a failed repair would store
     * SUCCEEDED if the outcome came from there.
     */
    private fun finishRun(
        claim: VectorIndexClaim,
        job: IndexJob<T>,
        report: VectorRebuildReport,
        failure: String?,
    ): Mono<Void> {
        val result = state.finish(claim, report, failure, job.key)

        logger.info(
            "Vector reindex finished. succeeded={}, attempted={}, indexed={}, skipped={}, failed={}",
            result.succeeded,
            report.attempted,
            report.indexed,
            report.skipped,
            report.failed,
        )

        return jobStore.finishJob(
            job.copy(
                finishedAt = report.finishedAt,
                outcome = if (result.succeeded) JobOutcome.SUCCEEDED else JobOutcome.FAILED,
                attempted = report.attempted,
                indexed = report.indexed,
                skipped = report.skipped,
                failed = report.failed,
                failureSummary = failure,
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun asText(message: Message<T, V>): Message<T, String> =
        message as Message<T, String>

    private fun summary(error: Throwable): String =
        "${error.javaClass.simpleName}: ${error.message ?: "No message"}"
}
