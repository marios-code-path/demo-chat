package com.demo.chat.service.composite.impl

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.service.vector.VectorIndexClaim
import com.demo.chat.domain.JobRecord
import com.demo.chat.service.vector.JobRecordWriter
import com.demo.chat.service.vector.VectorIndexJobStore
import com.demo.chat.service.vector.VectorIndexState
import com.demo.chat.service.vector.VectorIndexStatus
import com.demo.chat.service.vector.VectorIndexTriggerResult
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
    private val recordWriter: JobRecordWriter<T>,
    private val clock: Clock = Clock.systemUTC(),
    private val scheduler: Scheduler = Schedulers.boundedElastic(),
) : MessageReindexService<T> {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun status(): VectorIndexStatus<T> = state.status()

    override fun start(): Mono<VectorIndexTriggerResult<T>> = Mono.fromSupplier {
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
        VectorIndexTriggerResult(claim.accepted, claim.status)
    }

    /**
     * Releases a claim that no rebuild can finish. A rejected scheduler and an
     * unexpected terminal error both reach this path. Without the release the
     * phase stays REBUILDING and every later trigger reports busy.
     */
    private fun release(
        claim: VectorIndexClaim<T>,
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
        claim: VectorIndexClaim<T>,
        startedAt: Instant,
    ): Mono<Void> =
        jobStore.createJob(startedAt)
            .flatMap { job ->
                // The status names the running job from here on. This call runs
                // before the first record, so a reader of the job topic can
                // always find that job in the status.
                state.markActiveJob(job.key)
                // Two events per run, and both go to the job topic. A reader
                // of that topic learns when the run began and how it ended.
                emit(job, "rebuild started", null)
                    .then(exclusionFor(claim, startedAt, job))
                    .flatMap { exclusion -> scanWith(claim, startedAt, job, exclusion) }
            }

    /**
     * Publishes one job record.
     *
     * A failed record write never fails the run. The record is a report, and
     * losing it must not turn a healthy rebuild into a failed one.
     */
    private fun emit(job: IndexJob<T>, message: String, report: VectorRebuildReport?): Mono<Void> =
        // The id comes from the message store, like every other message id.
        // PersistenceStore.key() answers with a Mono, so this reads it rather
        // than taking a synchronous supplier that no deployment could provide.
        persistence.key()
            .flatMap { recordKey ->
                recordWriter.write(
                    JobRecord(
                        key = recordKey,
                        jobKey = job.key,
                        workerKey = job.startedBy,
                        at = clock.instant(),
                        message = message,
                        attempted = report?.attempted,
                        indexed = report?.indexed,
                        skipped = report?.skipped,
                        failed = report?.failed,
                    )
                )
            }
            .onErrorResume { error ->
                logger.error("Vector reindex could not write a job record", error)
                Mono.empty()
            }

    /**
     * The exclusion set for one run, or empty when the listing failed.
     *
     * One listing serves discovery and exclusion. A failure stops the run
     * before the scan, because a scan without the full exclusion set would
     * index job records.
     *
     * The recovery sits on the listing alone. A handler that also covered the
     * scan and the terminal write would finish one run twice, because a failed
     * terminal write would re-enter it.
     */
    private fun exclusionFor(
        claim: VectorIndexClaim<T>,
        startedAt: Instant,
        job: IndexJob<T>,
    ): Mono<Set<T>> =
        jobStore.listJobTopics()
            .map { topic -> topic.key.id }
            .collectList()
            // The store writes this job's topic before the listing runs, so a
            // store that reads its own writes already returns it. The union
            // makes the rule independent of that. A store listing from a
            // cache or a replica could omit a topic written moments earlier,
            // and the run would then index its own records.
            .map { topicIds -> topicIds.toSet() + job.key.id }
            .onErrorResume { error ->
                logger.error("Vector reindex could not list its job topics", error)
                finishRun(
                    claim,
                    job,
                    VectorRebuildReport(startedAt, clock.instant(), 0L, 0L, 0L, 0L),
                    summary(error),
                ).then(Mono.empty())
            }

    private fun scanWith(
        claim: VectorIndexClaim<T>,
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
                // record is not an inclusion rule here. MessagingServiceImpl
                // sets it true on every message it sends, and the indexer
                // applies its own rule, so a rebuild offers every message
                // that is not a job record.
                Mono.defer { indexer.add(asText(message)) }
                    .doOnSuccess { indexed.incrementAndGet() }
                    .onErrorResume { error ->
                        failed.incrementAndGet()
                        lastFailure.set(summary(error))
                        logger.error("Vector reindex message failed", error)
                        Mono.empty()
                    }
            }

        // The recovery covers the scan alone, so the terminal write runs once
        // whether the scan completed or failed. A handler wrapping both would
        // run the terminal write a second time when the first one failed.
        return scan
            .onErrorResume { error ->
                lastFailure.set(summary(error))
                logger.error("Vector reindex scan failed", error)
                Flux.empty()
            }
            .then(
                finish(claim, job, startedAt, attempted, indexed, skipped, failed, lastFailure)
            )
    }

    private fun finish(
        claim: VectorIndexClaim<T>,
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
        claim: VectorIndexClaim<T>,
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

        return emit(
            job,
            if (result.succeeded) "rebuild succeeded" else "rebuild failed",
            report,
        ).then(
            jobStore.finishJob(
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
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun asText(message: Message<T, V>): Message<T, String> =
        message as Message<T, String>

    private fun summary(error: Throwable): String =
        "${error.javaClass.simpleName}: ${error.message ?: "No message"}"
}
