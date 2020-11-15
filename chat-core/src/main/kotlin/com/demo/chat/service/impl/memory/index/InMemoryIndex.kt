package com.demo.chat.service.impl.memory.index

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyBearer
import com.demo.chat.service.IndexService
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
import kotlin.streams.asStream


interface IndexerFn<T> : Function<T, List<Pair<String, String>>>

open class InMemoryIndex<T, E : KeyBearer<T>, Q : Triple<String, String, Int>>(
        private val indexFn: IndexerFn<E>,
        private val queryKeyFn: Function<String, Key<T>>
) : IndexService<T, E, Q> {


    val analyzer = StandardAnalyzer()
    val directory = ByteBuffersDirectory()
    val config = IndexWriterConfig(analyzer)
    private val indexWriter = IndexWriter(directory, config)
    private val indexReader: DirectoryReader = DirectoryReader.open(directory)
    private val indexSearcher = IndexSearcher(indexReader)

    override fun add(entity: E): Mono<Void> = Mono.create {
        val doc = Document().apply {
            indexFn.apply(entity).forEach { kv ->
                add(Field(kv.first, kv.second, TextField.TYPE_NOT_STORED))
            }

            add(Field("key", entity.key.id.toString(), TextField.TYPE_STORED))
        }

        indexWriter.addDocument(doc)
    }

    override fun rem(key: Key<T>): Mono<Void> = Mono.create {
        val parser = QueryParser("key", analyzer)
        val query = parser.parse(key.id.toString())

        indexWriter.deleteDocuments(query)
    }

    override fun findBy(query: Q): Flux<out Key<T>> = Flux.create { sink ->
        val parser = QueryParser(query.first, analyzer)
        val luceneQuery = parser.parse(query.second)

        indexSearcher.search(luceneQuery, query.third).scoreDocs
                .asSequence()
                .asStream()
                .map {indexSearcher.doc(it.doc)}
                .map {it.get("key")}
                .map {queryKeyFn.apply(it)}
                .map (sink::next)
    }

}