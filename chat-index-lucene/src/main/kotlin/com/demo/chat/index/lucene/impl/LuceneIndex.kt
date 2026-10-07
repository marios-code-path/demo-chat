package com.demo.chat.index.lucene.impl

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.index.lucene.storage.IndexEntry
import com.demo.chat.index.lucene.storage.IndexFields
import com.demo.chat.index.lucene.storage.IndexStartSequence
import com.demo.chat.index.lucene.storage.LuceneStorage
import com.demo.chat.index.lucene.storage.MemoryStorage
import com.demo.chat.index.lucene.storage.StartOutcome
import com.demo.chat.index.lucene.storage.Started
import com.demo.chat.service.core.IndexService
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Function
import kotlin.concurrent.withLock

/**
 * One Lucene index. See CHAT-ybtirmgj.
 *
 * The index opens explicitly. The start load calls [open], and a test calls
 * [openInMemory]. Each runtime mutation changes the writer, commits, and
 * refreshes the searcher under one lock. A query only acquires and releases a
 * searcher, so it never waits for a mutation. The searcher manager reads
 * committed data only, so a query never sees an uncommitted change.
 */
open class LuceneIndex<T, E : Any>(
    private val entityEncoder: Function<E, List<Pair<String, String>>>,
    private val keyEncoder: Function<String, Key<T>>,
    private val keyReceiver: Function<E, Key<T>>,
) : IndexService<T, E, IndexSearchRequest> {

    val analyzer = StandardAnalyzer()

    private val logger = LoggerFactory.getLogger(javaClass)
    private val mutation = ReentrantLock()

    @Volatile private var state: IndexState = IndexState.NEW
    @Volatile private var started: Started? = null
    @Volatile private var failure: Throwable? = null
    @Volatile private var name: String = javaClass.simpleName

    /** A test seam. It runs after the writer change and before the commit. */
    internal var beforeCommit: () -> Unit = {}

    /** Runs the start sequence. A failure leaves the index FAILED and releases what the sequence obtained. */
    fun open(name: String, storage: LuceneStorage, entities: (() -> Flux<out E>)?) {
        mutation.withLock {
            check(state == IndexState.NEW) { "Lucene index $name was already opened. Its state is $state." }
            this.name = name
            try {
                val result = IndexStartSequence(name, storage, analyzer, ::entryOf, entities).run()
                started = result
                state = IndexState.OPEN
                logger.info(result.outcome.logLine(name))
            } catch (t: Throwable) {
                failure = t
                state = IndexState.FAILED
                throw t
            }
        }
    }

    fun openInMemory(name: String = javaClass.simpleName) = open(name, MemoryStorage(), null)

    fun state(): IndexState = state

    fun startOutcome(): StartOutcome? = started?.outcome

    fun liveDocuments(): Int? = if (state == IndexState.OPEN) withSearcher { it.indexReader.numDocs() } else null

    /** The fields are read and checked before any writer change, so an encoder error keeps the index OPEN. */
    internal fun entryOf(entity: E): IndexEntry =
        IndexEntry.of(keyReceiver.apply(entity).id.toString(), entityEncoder.apply(entity))

    /**
     * The state check runs before the encoder, so an encoder error cannot hide
     * a NEW, FAILED or CLOSED index. The mutation checks the state again under
     * the lock.
     */
    override fun add(entity: E): Mono<Void> =
        Mono.fromCallable { requireOpen(); entryOf(entity) }
            .flatMap { entry ->
                mutate { writer -> writer.updateDocument(Term(IndexFields.EXACT_KEY, entry.keyText), entry.document) }
            }

    /** Removes the document of one key, and only that document. The exact field is not analyzed. */
    override fun rem(key: Key<T>): Mono<Void> =
        mutate { writer -> writer.deleteDocuments(Term(IndexFields.EXACT_KEY, key.id.toString())) }

    override fun findBy(query: IndexSearchRequest): Flux<out Key<T>> = Flux.defer {
        val keys = withSearcher { searcher ->
            searcher.search(QueryParser(query.first, analyzer).parse(query.second), query.config).scoreDocs
                .map { searcher.doc(it.doc).get(IndexFields.STORED_KEY) }
        }
        Flux.fromIterable(keys.map(keyEncoder::apply))
    }

    override fun findUnique(query: IndexSearchRequest): Mono<out Key<T>> = findBy(query).singleOrEmpty()

    protected fun <R> withSearcher(read: (IndexSearcher) -> R): R {
        val manager = requireOpen().manager
        val searcher = manager.acquire()
        try {
            return read(searcher)
        } finally {
            manager.release(searcher)
        }
    }

    /**
     * Idempotent, and safe in every state. It waits for a running mutation. It
     * never commits, because the writer config sets commit-on-close to false.
     * The owner lock is released last.
     */
    fun close() {
        mutation.withLock {
            if (state == IndexState.CLOSED) return
            val held = started
            started = null
            state = IndexState.CLOSED
            val errors = mutableListOf<Throwable>()
            val step = { block: () -> Unit -> try { block() } catch (e: Throwable) { errors += e } }
            if (held != null) {
                step { held.manager.close() }
                step { held.writer.close() }
                step { held.directory.close() }
            }
            step { analyzer.close() }
            if (held != null) step { held.ownerLock.close() }
            errors.firstOrNull()?.let { first ->
                errors.drop(1).forEach(first::addSuppressed)
                throw first
            }
        }
    }

    private fun mutate(change: (IndexWriter) -> Unit): Mono<Void> = Mono.fromRunnable {
        mutation.withLock {
            val held = requireOpen()
            try {
                change(held.writer)
                beforeCommit()
                held.writer.commit()
                held.manager.maybeRefreshBlocking()
            } catch (t: Throwable) {
                fail(held, t)
                throw t
            }
        }
    }

    /** Best effort, and nothing commits. The owner lock stays held until close. */
    private fun fail(held: Started, cause: Throwable) {
        failure = cause
        state = IndexState.FAILED
        try { held.writer.rollback() } catch (e: Throwable) { cause.addSuppressed(e) }
        try { held.manager.close() } catch (e: Throwable) { cause.addSuppressed(e) }
    }

    private fun requireOpen(): Started = when (state) {
        IndexState.OPEN -> started ?: throw IllegalStateException("Lucene index $name is closed")
        IndexState.NEW -> throw IllegalStateException("Lucene index $name is not open")
        IndexState.FAILED -> throw IllegalStateException("Lucene index $name failed: ${failure?.message}", failure)
        IndexState.CLOSED -> throw IllegalStateException("Lucene index $name is closed")
    }
}
