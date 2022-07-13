package com.demo.chat.service.impl.index.lucene

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.service.MembershipIndexService
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import reactor.core.publisher.Mono
import java.util.function.Function

open class MembershipLuceneIndex<T>(
    entityEncoder: Function<TopicMembership<T>, List<Pair<String, String>>>,
    keyEncoder: Function<String, Key<T>>,
    keyReceiver: Function<TopicMembership<T>, Key<T>>,
) : LuceneIndex<T, TopicMembership<T>>(entityEncoder, keyEncoder, keyReceiver),
    MembershipIndexService<T, IndexSearchRequest> {
    override fun size(query: IndexSearchRequest): Mono<Long> = Mono.create { sink ->
        val indexReader: DirectoryReader = DirectoryReader.open(directory)
        val indexSearcher = IndexSearcher(indexReader)

        val hit = indexSearcher.search(QueryParser(query.first, analyzer)
            .parse(query.second), query.third)
            .totalHits
        sink.success(hit.value)
    }
}