package com.demo.chat.index.lucene.impl

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.service.core.IndexService
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
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

    override fun add(entity: E): Mono<Void> = Mono.create { sink ->
        val doc = Document().apply {
            entityEncoder.apply(entity).forEach { kv ->
                add(Field(kv.first, kv.second, TextField.TYPE_NOT_STORED))
            }
            add(Field("key", keyReceiver.apply(entity).id.toString(), TextField.TYPE_STORED))
        }

        writer.addDocument(doc)
        writer.commit()

        sink.success()
    }

    override fun rem(key: Key<T>): Mono<Void> = Mono.create { sink ->
        val parser = QueryParser("key", analyzer)
        val query = parser.parse("+${key.id.toString()}")

        writer.deleteDocuments(query)
        writer.commit()

        sink.success()

    }

    override fun findBy(query: IndexSearchRequest): Flux<out Key<T>> = Flux.create { sink ->
        val indexReader: DirectoryReader = DirectoryReader.open(directory)
        val indexSearcher = IndexSearcher(indexReader)

        val doc =
            indexSearcher.search(QueryParser(query.first, analyzer).parse(query.second), query.third).scoreDocs
                .map {
                    indexSearcher
                        .doc(it.doc)
                        .get("key")
                }
                .map(keyEncoder::apply)

        doc.forEach { d -> sink.next(d) }

        sink.complete()
    }

    override fun findUnique(query: IndexSearchRequest): Mono<out Key<T>> =
        findBy(query)
            .singleOrEmpty()
}