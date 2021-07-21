package com.demo.chat.secure

import com.demo.chat.domain.Key
import com.demo.chat.service.AuthMetadata
import reactor.core.publisher.Flux

// 1: requires a principal entity id = Pid
// 2: requires all wildcard id's = Wid[n]
interface Filterizer<M, T> {
    fun filterize(elements: Flux<M>, hitElements: Sequence<T>): Flux<M>
}

class AuthFilterizer<T, P> : Filterizer<AuthMetadata<T, P>, Key<T>> {
    override fun filterize(elements: Flux<AuthMetadata<T, P>>, hitElements: Sequence<Key<T>>): Flux<AuthMetadata<T, P>> =
        elements
            .filter { meta -> (meta.expires == 0L || meta.expires > System.currentTimeMillis()) }
            .filter { meta ->
                val eP = meta.principal
                val eT = meta.target

                hitElements
                    .filter { k -> (k == eP || k == eT) }
                    .any()
            }
            .groupBy { g -> g.permission }
            .flatMap { g -> g.reduce { a, _ -> a } }
}