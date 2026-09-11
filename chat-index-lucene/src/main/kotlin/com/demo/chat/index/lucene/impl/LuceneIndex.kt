package com.demo.chat.index.lucene.impl

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.service.core.IndexService
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.ByteBuffersDirectory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

open class LuceneIndex<T, E>(
    private val entityEncoder: Function<E, List<Pair<String, String>>>,
    private val keyEncoder: Function<String, Key<T>>,
    private val keyReceiver: Function<E, Key<T>>,
) : IndexService<T, E, IndexSearchRequest> {

    val analyzer = StandardAnalyzer()
    val directory = ByteBuffersDirectory()
    private val writer = IndexWriter(directory, IndexWriterConfig(analyzer))

    init {
        writer.deleteAll()
        writer.commit()
    }

    fun onClose() {
        writer.use {
            it.close()
        }
    }

    override fun add(entity: E): Mono<Void> =
        Mono.defer { addEntry(entityEncoder.apply(entity), keyReceiver.apply(entity)) }

    /**
     * Stores one document for [key] with [fields].
     *
     * A caller that already read the fields uses this, so the encoder runs
     * once per add. A second encoder call could fail after a removal already
     * ran, which would remove the entry and store nothing in its place.
     */
    protected fun addEntry(fields: List<Pair<String, String>>, key: Key<T>): Mono<Void> =
        Mono.create { sink ->
            val doc = Document().apply {
                fields.forEach { kv ->
                    add(Field(kv.first, kv.second, TextField.TYPE_NOT_STORED))
                }
                add(Field("key", key.id.toString(), TextField.TYPE_STORED))
                add(StringField(EXACT_KEY, key.id.toString(), Field.Store.NO))
            }

            writer.addDocument(doc)
            writer.commit()

            sink.success()
        }

    /**
     * Removes the document of one key, and only that document.
     *
     * The `key` field is analyzed, so a parsed query never matched one key.
     * StandardAnalyzer splits a uuid at its separators, and a hyphen is the
     * QueryParser NOT operator, so removing one uuid deleted every document
     * that shared a segment. The removal matches the exact field instead.
     */
    override fun rem(key: Key<T>): Mono<Void> = Mono.create { sink ->
        writer.deleteDocuments(Term(EXACT_KEY, key.id.toString()))
        writer.commit()

        sink.success()
    }

    override fun findBy(query: IndexSearchRequest): Flux<out Key<T>> = Flux.create { sink ->
        val indexReader: DirectoryReader = DirectoryReader.open(directory)
        val indexSearcher = IndexSearcher(indexReader)

        val doc =
            indexSearcher.search(QueryParser(query.first, analyzer).parse(query.second), query.config).scoreDocs
                .map {
                    indexSearcher
                        .doc(it.doc)
                        .get("key")
                }
                .map(keyEncoder::apply)

        doc.forEach { d ->
            sink.next(d)
        }

        sink.complete()
    }

    override fun findUnique(query: IndexSearchRequest): Mono<out Key<T>> =
        findBy(query)
            .singleOrEmpty()

    companion object {
        /** Internal, and not analyzed. A removal matches this field exactly. */
        const val EXACT_KEY = "_key"
    }
}
