package com.demo.chat.service.impl.memory.auth

import com.demo.chat.service.AuthorizationService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import java.util.function.Supplier

class AuthorizationInMemory<T, M>(
    private val idForUser: Function<M, T>,
    private val idForTarget: Function<M, T>,
    private val anonUid: Supplier<T>
) : AuthorizationService<T, M> {
    private val authorizations = ConcurrentHashMap<T, ArrayList<M>>()

    override fun authorize(authorization: M, exist: Boolean): Mono<Void> = Mono.create { sink ->
        val uid = idForUser.apply(authorization)

        if (authorizations.containsKey(uid)) {
            val auths = authorizations[uid]!!
            if (exist)
                auths.add(authorization)
            else
                auths.remove(authorization)
        }
        sink.success()
    }

    override fun getAuthorizationsFor(uid: T): Flux<M> = Flux.create { sink ->
        if (authorizations.containsKey(uid)) {
            val auths = authorizations[uid]!!
            auths.forEach { m -> sink.next(m) }
        }
        sink.complete()
    }

    override fun getAuthorizationsAgainst(uid: T, target: T): Flux<M> =
        Flux.concat(getAuthorizationsFor(anonUid.get()), getAuthorizationsFor(uid), getAuthorizationsFor(target))
            .filter {
                val sid = idForUser.apply(it)
                val tid = idForTarget.apply(it)
                (sid == anonUid.get() || sid == uid || tid == target || tid == sid == uid)
            }
}