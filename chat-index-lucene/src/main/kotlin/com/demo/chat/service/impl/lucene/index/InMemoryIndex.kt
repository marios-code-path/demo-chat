package com.demo.chat.service.impl.lucene.index

import com.demo.chat.domain.IndexSearchRequest
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

fun interface IndexEntryEncoder<E> : Function<E, List<Pair<String, String>>>
fun interface StringToKeyEncoder<T> : Function<String, Key<T>>

open class InMemoryIndex<T, E : KeyBearer<T>>(
        private val entityEncoder: Function<E, List<Pair<String, String>>>,
        private val keyEncoder: Function<String, Key<T>>
) : IndexService<T, E, IndexSearchRequest> {


    private val analyzer = StandardAnalyzer()
    private val directory = ByteBuffersDirectory()

    override fun add(entity: E): Mono<Void> //Mono.create { sink ->
    {
        val doc = Document().apply {
            entityEncoder.apply(entity).forEach { kv ->
                add(Field(kv.first, kv.second, TextField.TYPE_NOT_STORED))
            }
            add(Field("key", entity.key.id.toString(), TextField.TYPE_STORED))
        }

        IndexWriter(directory, IndexWriterConfig(analyzer)).use {
            it.addDocument(doc)
            it.commit()
            it.close()
        }

        //sink.success()
        return Mono.empty()
    }

    override fun rem(key: Key<T>): Mono<Void>  {
        val parser = QueryParser("key", analyzer)
        val query = parser.parse("+${key.id.toString()}")

        IndexWriter(directory, IndexWriterConfig(analyzer)).use {
            it.deleteDocuments(query)
            it.commit()
        }

        return Mono.empty()
    }

    override fun findBy(query: IndexSearchRequest): Flux<out Key<T>> {
        val indexReader: DirectoryReader = directory.use { DirectoryReader.open(it) }
        val indexSearcher = IndexSearcher(indexReader)

        val doc = directory.use {
            indexSearcher.search(QueryParser(query.first, analyzer).parse(query.second), query.third).scoreDocs
                    .map {
                        indexSearcher
                                .doc(it.doc)
                                .get("key")
                    }
                    .map(keyEncoder::apply)
        }

        return Flux.fromIterable(doc)
    }
}