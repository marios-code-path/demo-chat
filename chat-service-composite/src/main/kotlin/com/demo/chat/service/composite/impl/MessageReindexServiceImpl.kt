package com.demo.chat.service.composite.impl

import com.demo.chat.domain.Message
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.service.vector.VectorIndexClaim
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
    private val state: VectorIndexState,
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
        )
    }

    private fun rebuild(
        claim: VectorIndexClaim,
        startedAt: Instant,
    ): Mono<Void> {
        val attempted = AtomicLong()
        val indexed = AtomicLong()
        val skipped = AtomicLong()
        val failed = AtomicLong()
        val lastFailure = AtomicReference<String?>()

        val scan = Flux.defer { persistence.all() }
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
        startedAt: Instant,
        attempted: AtomicLong,
        indexed: AtomicLong,
        skipped: AtomicLong,
        failed: AtomicLong,
        lastFailure: AtomicReference<String?>,
    ): Mono<Void> = Mono.fromRunnable<Void> {
        val report = VectorRebuildReport(
            startedAt,
            clock.instant(),
            attempted.get(),
            indexed.get(),
            skipped.get(),
            failed.get(),
        )
        state.finish(claim, report, lastFailure.get())
        logger.info(
            "Vector reindex finished. attempted={}, indexed={}, skipped={}, failed={}",
            report.attempted,
            report.indexed,
            report.skipped,
            report.failed,
        )
    }.then()

    @Suppress("UNCHECKED_CAST")
    private fun asText(message: Message<T, V>): Message<T, String> =
        message as Message<T, String>

    private fun summary(error: Throwable): String =
        "${error.javaClass.simpleName}: ${error.message ?: "No message"}"
}
