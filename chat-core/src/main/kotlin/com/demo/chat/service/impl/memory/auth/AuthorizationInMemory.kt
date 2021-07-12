package com.demo.chat.service.impl.memory.auth

import com.demo.chat.service.AuthorizationService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Supplier

class AuthorizationInMemory<T, M, G>(
    private val idForUser: Function<M, T>,
    private val idForTarget: Function<M, T>,
    private val anonUid: Supplier<T>,
    private val grouper: Function<M, G>,
    private val reducer: BiFunction<M, M, M>
) : AuthorizationService<T, M> {
    private val authorizations = ConcurrentHashMap<T, HashSet<M>>()

    override fun authorize(authorization: M, exist: Boolean): Mono<Void> = Mono.create { sink ->
        val uid = idForUser.apply(authorization)

        if (!authorizations.containsKey(uid)) {
            authorizations[uid] = LinkedHashSet()
        }

        val auths = authorizations[uid]!!

        if (exist)
            auths.add(authorization)
        else
            auths.remove(authorization)

        sink.success()
    }

    override fun getAuthorizationsFor(uid: T): Flux<M> = Flux.create { sink ->
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