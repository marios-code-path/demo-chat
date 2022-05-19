package com.demo.chat.secure

import com.demo.chat.domain.Key
import com.demo.chat.service.AuthMetadata
import reactor.core.publisher.Flux

// 1: requires a principal entity id = Pid
// 2: requires all wildcard id's = Wid[n]
interface Filterizer<M, T> {
    fun computeAggregates(elements: Flux<M>, targetIds: Sequence<T>): Flux<M>
}

class AuthFilterizer<T, P>(private val comparator: Comparator<AuthMetadata<T, P>>) : Filterizer<AuthMetadata<T, P>, Key<T>> {
    override fun computeAggregates(elements: Flux<AuthMetadata<T, P>>, targetIds: Sequence<Key<T>>): Flux<AuthMetadata<T, P>> =
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