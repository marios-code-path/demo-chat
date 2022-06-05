package com.demo.chat.secure

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import reactor.core.publisher.Flux

// 1: requires a principal entity id = Pid
// 2: requires all wildcard id's = Wid[n]
interface Summarizer<M, T> {
    fun computeAggregates(elements: Flux<M>, targetIds: Sequence<T>): Flux<M>
}

class AuthSummarizer<T>(private val comparator: Comparator<AuthMetadata<T>>) : Summarizer<AuthMetadata<T>, Key<T>> {
    override fun computeAggregates(elements: Flux<AuthMetadata<T>>, targetIds: Sequence<Key<T>>): Flux<AuthMetadata<T>> =
        elements
            .filter { meta ->
                val principalId = meta.principal

                targetIds
                    .filter { targetId -> (targetId == principalId) }
                    .any()
            }
            .groupBy { g -> g.permission }
            .flatMap { g -> g.sort(comparator).last() }
            .filter { meta -> (meta.expires == 0L || meta.expires > System.currentTimeMillis()) }

}