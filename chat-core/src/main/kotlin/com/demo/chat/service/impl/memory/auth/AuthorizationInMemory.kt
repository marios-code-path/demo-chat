package com.demo.chat.service.impl.memory.auth

import com.demo.chat.domain.Key
import com.demo.chat.service.AuthorizationService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Supplier

class AuthorizationInMemory<T, M, G, Q>(
    private val authIndex: IndexService<T, M, Q>,
    private val idForUser: Function<M, Key<T>>,
    private val idForTarget: Function<M, Key<T>>,
    private val keyForAuth: Function<M, Key<T>>
    private val anonUid: Supplier<Key<T>>,
    private val grouper: Function<M, G>,
    private val reducer: BiFunction<M, M, M>
) : AuthorizationService<T, M> {
    override fun authorize(authorization: M, exist: Boolean): Mono<Void> =
        when(exist) {
            true -> authIndex.add(authorization)
            else -> authIndex.rem(keyForAuth.apply(authorization))
        }

    override fun getAuthorizationsFor(uid: T): Flux<M> =
        authIndex.findBy(authQueryForID.apply(uid))
    
        Flux.create { sink ->
        if (authorizations.containsKey(uid)) {
            val auths = authorizations[uid]!!
            auths.forEach { m -> sink.next(m) }
        }
        sink.complete()
    }

    override fun getAuthorizationsAgainst(userKey: T, targetKey: T): Flux<M> =
        Flux.merge(
            getAuthorizationsFor(anonUid.get()),
            getAuthorizationsFor(userKey),
            getAuthorizationsFor(targetKey)
        )
            .filter {
                val uid = idForUser.apply(it)
                val tid = idForTarget.apply(it)
                (userKey == uid && targetKey == tid) ||
                        (uid == anonUid.get() && tid == anonUid.get() ) ||
                        (uid == anonUid.get() && tid == userKey) ||
                        (uid == userKey && tid == userKey)
            }
            .groupBy(grouper)
            .flatMap { g -> g.reduce(reducer) }
}