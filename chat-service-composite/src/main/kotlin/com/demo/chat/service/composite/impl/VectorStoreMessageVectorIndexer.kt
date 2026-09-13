package com.demo.chat.service.composite.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.service.vector.MessageDocumentMapper
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.service.vector.VectorIndexJobStore
import com.demo.chat.service.vector.VectorIndexState
import org.slf4j.LoggerFactory
import org.springframework.ai.vectorstore.VectorStore
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Clock

/**
 * Bridges the blocking VectorStore to the send chain. Every Spring AI call
 * runs on Schedulers.boundedElastic(). record == false (join and leave
 * alerts) returns success without a write.
 *
 * A failed add removes coverage. The index lost a message, so the covering job
 * no longer describes the index.
 */
class VectorStoreMessageVectorIndexer<T>(
    private val vectorStore: VectorStore,
    private val mapper: MessageDocumentMapper<T>,
    private val state: VectorIndexState<T>,
    private val jobStore: VectorIndexJobStore<T>,
    private val clock: Clock = Clock.systemUTC(),
) : MessageVectorIndexer<T> {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun add(message: Message<T, String>): Mono<Void> =
        if (!message.record) {
            Mono.empty()
        } else {
            Mono.fromCallable {
                vectorStore.add(listOf(mapper.toDocument(message)))
            }
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorResume { error -> recordFailure(error) }
        }

    // A failed delete leaves a stale document. Stale document removal is out of
    // scope for this design, so this path keeps its current behavior.
    override fun remove(key: Key<T>): Mono<Void> =
        Mono.fromCallable {
            vectorStore.delete(listOf(mapper.documentId(key.id)))
        }
            .subscribeOn(Schedulers.boundedElastic())
            .then()

    /**
     * Removes coverage and returns the original error.
     *
     * The in-process state drops its target first. The durable count on that
     * job makes the removal survive a restart under stored trust.
     *
     * A failed durable write logs and never replaces the vector error. The send
     * chain decides what to do with that error, so it must see the real one.
     *
     * A rebuild scan also reaches this path. A failed message then raises the
     * generation, and the run cannot install its job. That outcome is correct,
     * because the index lost a message.
     *
     * A null target is not a failure. No job covered the index, so no durable
     * count can fall. The process generation still moves.
     */
    private fun recordFailure(error: Throwable): Mono<Void> {
        val invalidation = state.invalidate(summary(error))
        val target = invalidation.target ?: return Mono.error(error)
        return jobStore.invalidate(target, clock.instant())
            .onErrorResume { writeError ->
                logger.error("Vector index could not raise the invalidation count", writeError)
                Mono.empty()
            }
            .then(Mono.error(error))
    }

    private fun summary(error: Throwable): String =
        "${error.javaClass.simpleName}: ${error.message ?: "No message"}"
}
