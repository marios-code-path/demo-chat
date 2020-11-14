package com.demo.chat.service.impl.memory.index

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import org.apache.lucene.analysis.SimpleAnalyzer
import org.apache.lucene.index.memory.MemoryIndex
import org.apache.lucene.util.Version
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

interface IndexerFn<T> : Function<T, List<Pair<String, String>>>

open class InMemoryIndex<T, E, Q>(private val indexFn: IndexerFn<E>) : IndexService<T, E, Q> {
    val index = MemoryIndex()
    val analyzer = SimpleAnalyzer(Version.LUCENE_36)

    override fun add(entity: E): Mono<Void> = Flux
            .fromStream(indexFn.apply(entity).stream())
            .map { index.addField(it.first, it.second, analyzer)}
            .then()

    override fun rem(key: Key<T>): Mono<Void> {
        TODO("Not yet implemented")
    }

    override fun findBy(query: Q): Flux<out Key<T>> {
        TODO("Not yet implemented")
    }

}