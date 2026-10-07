package com.demo.chat.index.lucene.storage

import com.demo.chat.domain.ChatException
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexNotFoundException
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.IndexWriterConfig.OpenMode
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.Directory
import org.apache.lucene.store.Lock
import org.apache.lucene.store.LockObtainFailedException
import org.apache.lucene.util.BytesRef
import org.apache.lucene.util.FixedBitSet
import reactor.core.publisher.Flux
import java.io.IOException

/** Held from open to close. No other process can use the index directory. */
const val OWNER_LOCK = "chat-owner.lock"

/** Recovery never deletes these files. */
private val KEPT_FILES = setOf(
    IndexWriter.WRITE_LOCK_NAME, OWNER_LOCK, IndexRequest.REBUILD.fileName, IndexRequest.DROP.fileName,
)

/** What a successful start holds. The index owns these until it closes. */
class Started(
    val directory: Directory,
    val ownerLock: Lock,
    val writer: IndexWriter,
    val manager: SearcherManager,
    val outcome: StartOutcome,
)

/**
 * The start sequence of one Lucene index. See the spec section "The start
 * sequence for one index", CHAT-ybtirmgj.
 *
 * The compare is exact only when no other process writes the store during the
 * start. CHAT-lswjobhz holds that limit.
 */
class IndexStartSequence<E : Any> internal constructor(
    private val name: String,
    private val storage: LuceneStorage,
    private val analyzer: Analyzer,
    private val entryOf: (E) -> IndexEntry,
    private val entities: (() -> Flux<out E>)?,
    private val rollback: (IndexWriter) -> Unit,
) {
    constructor(
        name: String,
        storage: LuceneStorage,
        analyzer: Analyzer,
        entryOf: (E) -> IndexEntry,
        entities: (() -> Flux<out E>)?,
    ) : this(name, storage, analyzer, entryOf, entities, { it.rollback() })

    private val header = IndexHeader.current(name, storage.keyType, storage.nodeId, analyzer)

    private sealed interface Decision
    private data class Reuse(val compared: Long) : Decision
    private data class Build(val reason: BuildReason, val compared: Long = 0) : Decision
    private data class Recover(val reason: BuildReason) : Decision

    /** A damaged read inside the compare. The sequence recovers from it. */
    private class DamageFound(cause: Throwable?) : RuntimeException(cause)

    fun run(): Started {
        if (entities == null && storage.persistent) {
            throw ChatException(
                "${LuceneStorages.ROOT} is set, and no local store exists for Lucene index $name. " +
                    "The index files cannot be checked against a store."
            )
        }
        val scan: () -> Flux<out E> = entities ?: { Flux.empty() }
        val directory = storage.open(name)
        val ownerLock = try {
            directory.obtainLock(OWNER_LOCK)
        } catch (t: Throwable) {
            runCatching { directory.close() }.onFailure(t::addSuppressed)
            if (t is LockObtainFailedException) {
                throw ChatException(
                    "Lucene index $name is in use by another process at ${storage.describe(name)}. Nothing was deleted.", t,
                )
            }
            throw t
        }
        var writer: IndexWriter? = null
        try {
            val requests = storage.requests(name)
            requests?.removeStaleTemporary()
            val pending = requests?.pending()
            writer = openWriter(directory, OpenMode.CREATE_OR_APPEND)
            val decision = when {
                pending == IndexRequest.DROP -> Recover(BuildReason.REQUESTED_DROP)
                writer == null -> Recover(BuildReason.DAMAGE)
                pending == IndexRequest.REBUILD -> Build(BuildReason.REQUESTED_REBUILD)
                else -> inspect(directory, scan)
            }
            if (decision is Recover) writer = recover(directory, writer)
            val active = writer!!
            val outcome = when (decision) {
                is Reuse -> StartOutcome(StartKind.REUSED, null, decision.compared, 0)
                is Build -> StartOutcome(
                    StartKind.BUILT, decision.reason, decision.compared,
                    build(active, scan, requests, IndexRequests.clearedBy(pending)),
                )
                is Recover -> StartOutcome(
                    StartKind.BUILT, decision.reason, 0,
                    build(active, scan, requests, IndexRequests.clearedBy(pending)),
                )
            }
            // Every build syncs the directory inside build(), before this manager exists.
            return Started(directory, ownerLock, active, SearcherManager(directory, null), outcome)
        } catch (t: Throwable) {
            writer?.let { rollbackQuietly(it, t) }
            runCatching { directory.close() }.onFailure(t::addSuppressed)
            runCatching { ownerLock.close() }.onFailure(t::addSuppressed)
            throw t
        }
    }

    private fun config(mode: OpenMode) = IndexWriterConfig(analyzer).setOpenMode(mode).setCommitOnClose(false)

    /** Returns null when the files are damaged. Any other error fails the start. */
    private fun openWriter(directory: Directory, mode: OpenMode): IndexWriter? = try {
        IndexWriter(directory, config(mode))
    } catch (e: IOException) {
        if (Damage.isDamage(e)) null else throw e
    }

    private fun inspect(directory: Directory, scan: () -> Flux<out E>): Decision {
        val reader = try {
            DirectoryReader.open(directory)
        } catch (e: IndexNotFoundException) {
            return Build(BuildReason.NO_COMMIT)
        } catch (e: IOException) {
            if (Damage.isDamage(e)) return Recover(BuildReason.DAMAGE) else throw e
        }
        reader.use {
            if (IndexHeader.read(reader.indexCommit.userData) != header) return Build(BuildReason.HEADER)
            try {
                reader.leaves().forEach { leaf -> leaf.reader().checkIntegrity() }
            } catch (e: IOException) {
                if (Damage.isDamage(e)) return Recover(BuildReason.DAMAGE) else throw e
            }
            return try {
                compare(reader, scan)
            } catch (e: DamageFound) {
                Recover(BuildReason.DAMAGE)
            }
        }
    }

    /**
     * Each store entity must match exactly one live document with equal bytes.
     * A lookup that reaches a marked document proves a repeated store key.
     */
    private fun compare(reader: DirectoryReader, scan: () -> Flux<out E>): Decision {
        val searcher = IndexSearcher(reader)
        val seen = FixedBitSet(maxOf(reader.maxDoc(), 1))
        var compared = 0L
        scan().toStream().use { stream ->
            for (entity in stream.iterator()) {
                val entry = entryOf(entity)
                compared++
                val hits = lucene { searcher.search(TermQuery(Term(IndexFields.EXACT_KEY, entry.keyText)), 2).scoreDocs }
                if (hits.size != 1) return Build(BuildReason.MISMATCH, compared)
                val doc = hits[0].doc
                if (seen.get(doc)) {
                    throw ChatException(
                        "The store emitted key ${entry.keyText} more than once. Lucene index $name cannot be checked against it."
                    )
                }
                seen.set(doc)
                val stored = lucene { searcher.doc(doc, setOf(IndexFields.ENTRY)).getBinaryValue(IndexFields.ENTRY) }
                    ?: throw DamageFound(null)
                if (!stored.bytesEquals(BytesRef(entry.bytes))) return Build(BuildReason.MISMATCH, compared)
            }
        }
        return if (reader.numDocs().toLong() == compared) Reuse(compared) else Build(BuildReason.MISMATCH, compared)
    }

    private inline fun <R> lucene(read: () -> R): R = try {
        read()
    } catch (e: IOException) {
        if (Damage.isDamage(e)) throw DamageFound(e) else throw e
    }

    /**
     * Deletes every index file under write.lock. Neither lock nor any request
     * file is deleted. A rollback error stops recovery before any deletion,
     * because a writer that did not roll back can still hold write.lock or
     * pending files.
     */
    private fun recover(directory: Directory, writer: IndexWriter?): IndexWriter {
        if (writer != null && writer.isOpen) rollback(writer)
        val writeLock = try {
            directory.obtainLock(IndexWriter.WRITE_LOCK_NAME)
        } catch (e: LockObtainFailedException) {
            throw ChatException(
                "Lucene index $name needs recovery, and another holder keeps ${IndexWriter.WRITE_LOCK_NAME}. Nothing was deleted.", e,
            )
        }
        writeLock.use {
            directory.listAll().filterNot { it in KEPT_FILES }.forEach(directory::deleteFile)
        }
        return IndexWriter(directory, config(OpenMode.CREATE))
    }

    /** Checks every condition before the one commit. Returns the documents written. */
    private fun build(
        writer: IndexWriter,
        scan: () -> Flux<out E>,
        requests: IndexRequests?,
        cleared: List<IndexRequest>,
    ): Long {
        var scanned = 0L
        try {
            writer.deleteAll()
            scan().toStream().use { stream ->
                stream.forEach { entity ->
                    val entry = entryOf(entity)
                    writer.updateDocument(Term(IndexFields.EXACT_KEY, entry.keyText), entry.document)
                    scanned++
                }
            }
            DirectoryReader.open(writer, true, false).use { pending ->
                if (pending.numDocs().toLong() != scanned) {
                    throw ChatException(
                        "The store emitted at least one key more than once. Lucene index $name was not committed."
                    )
                }
            }
        } catch (t: Throwable) {
            rollbackQuietly(writer, t)
            throw t
        }
        writer.setLiveCommitData(header.userData().entries)
        try {
            writer.commit()
        } catch (t: Throwable) {
            rollbackQuietly(writer, t)
            throw ChatException(
                "The build commit of Lucene index $name failed. The committed state is uncertain. The next start checks it.", t,
            )
        }
        requests?.clear(cleared)
        storage.sync(name)
        return scanned
    }

    private fun rollbackQuietly(writer: IndexWriter, primary: Throwable?) {
        try {
            if (writer.isOpen) writer.rollback()
        } catch (e: Throwable) {
            primary?.addSuppressed(e)
        }
    }
}
