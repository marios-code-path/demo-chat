package com.demo.chat.test.index.lucene.storage

import com.demo.chat.index.lucene.storage.IndexEntry
import com.demo.chat.index.lucene.storage.IndexStartSequence
import com.demo.chat.index.lucene.storage.LuceneStorage
import com.demo.chat.index.lucene.storage.Started
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import reactor.core.publisher.Flux
import java.io.IOException

/** One stored entity of the tests: a key text and one field. */
data class Row(val key: String, val name: String)

fun encode(row: Row): IndexEntry = IndexEntry.of(row.key, listOf("name" to row.name))

/**
 * A store stub. It counts each scan. A scan that matches [failOnScan] fails at
 * once.
 *
 * A store error cannot be placed after a chosen row. Reactor 3.8.7
 * `BlockingIterable` reports a terminal error as soon as it arrives, even while
 * rows are still queued. So a test that needs a failure after a writer update
 * uses [FailingEncoder].
 */
class RowStore(var rows: List<Row>, private val failOnScan: Int? = null) {
    var scans = 0
        private set

    fun all(): Flux<Row> = Flux.defer {
        scans++
        if (scans == failOnScan) Flux.error(IOException("injected store failure")) else Flux.fromIterable(rows)
    }
}

/**
 * An encoder that fails on one key during one scan. It counts the rows that it
 * encoded in that scan before the failure. A row that the build encoded is
 * also written to the writer before the next row is read.
 */
class FailingEncoder(private val store: RowStore, private val failKey: String, private val onScan: Int) {
    var encodedInFailingScan = 0
        private set

    fun entryOf(row: Row): IndexEntry {
        if (store.scans == onScan) {
            if (row.key == failKey) throw IllegalStateException("injected encoder failure")
            encodedInFailingScan++
        }
        return encode(row)
    }
}

fun start(
    storage: LuceneStorage,
    store: RowStore?,
    name: String = "user",
    entry: (Row) -> IndexEntry = ::encode,
    rollback: (IndexWriter) -> Unit = { it.rollback() },
): Started =
    IndexStartSequence(name, storage, StandardAnalyzer(), entry, store?.let { s -> { s.all() } }, rollback).run()

fun Started.closeAll() {
    manager.close()
    writer.close()
    directory.close()
    ownerLock.close()
}

fun Started.holds(key: String): Boolean = DirectoryReader.open(directory).use { reader ->
    IndexSearcher(reader).search(TermQuery(Term("_key", key)), 2).scoreDocs.size == 1
}
