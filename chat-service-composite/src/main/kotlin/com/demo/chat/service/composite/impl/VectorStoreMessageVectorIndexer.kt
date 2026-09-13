package com.demo.chat.service.composite.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.service.vector.MessageDocumentMapper
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.service.vector.VectorIndexJobStore
import com.demo.chat.service.vector.VectorIndexState
import com.demo.chat.service.vector.VectorWriteMode
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
 *
 * A write replaces. The document id is derived from the message id, so a
 * rebuild meets an id it already wrote. The embedded store refuses a repeat
 * with `Duplicate id`, which failed every rebuild after the first one in one
 * process.
 *
 * [writeMode] follows the provider. Only the embedded provider needs the
 * removal, and a removal on redis would log an error for every new message.
 */
class VectorStoreMessageVectorIndexer<T>(
    private val vectorStore: VectorStore,
    private val mapper: MessageDocumentMapper<T>,
    private val state: VectorIndexState<T>,
    private val jobStore: VectorIndexJobStore<T>,
    private val writeMode: VectorWriteMode,
    private val clock: Clock = Clock.systemUTC(),
) : MessageVectorIndexer<T> {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun add(message: Message<T, String>): Mono<Void> =
        if (!message.record) {
            Mono.empty()
        } else {
            // One callable holds both store calls, so the pair runs on one
            // bounded elastic worker and never splits across two.
            Mono.fromCallable { replace(message) }
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorResume { error -> recordFailure(error) }
        }

    /**
     * Writes the document of one message, over any document it already has.
     *
     * The mapping runs first. A mapping failure then leaves the stored document
     * in place, because no store call has run yet.
     *
     * [VectorWriteMode] decides the rest. `UPSERT` writes once, because the
     * provider overwrites a document of the same id. `DELETE_THEN_ADD` removes
     * the old document first, because the embedded provider refuses a repeated
     * id.
     *
     * **`DELETE_THEN_ADD` is not atomic.** Recall can miss this message between
     * the two calls. **The index still reports complete during that window**,
     * because a repair keeps the coverage of the older successful job. So a
     * caller can read `indexComplete=true` and miss one message. That is an
     * accepted false positive.
     *
     * The window runs from the completion of the removal to the completion of
     * the write. The write embeds the text and then commits it in the provider,
     * so the window covers both. **The design gives that interval no duration
     * bound.** A slow embedding call or a slow commit holds the message absent
     * for as long as it takes.
     *
     * A removal failure stops the write. It does **not** promise that the old
     * document survives. A provider can remove the document and then fail while
     * it commits. Three things hold:
     *
     * - The write does not run.
     * - The caller receives the removal error.
     * - The failure removes coverage, through [recordFailure].
     */
    private fun replace(message: Message<T, String>) {
        val document = mapper.toDocument(message)
        if (writeMode == VectorWriteMode.DELETE_THEN_ADD) {
            vectorStore.delete(listOf(document.id))
        }
        vectorStore.add(listOf(document))
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
