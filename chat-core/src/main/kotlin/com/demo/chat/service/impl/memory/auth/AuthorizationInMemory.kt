package com.demo.chat.service.impl.memory.auth

import com.demo.chat.service.AuthorizationService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

class AuthorizationInMemory<T, M>(
    private val authForUser: Function<M, T>
) : AuthorizationService<T, M> {
    private val authorizations = ConcurrentHashMap<T, ArrayList<M>>()

    override fun authorize(authorization: M, exist: Boolean): Mono<Void> = Mono.create { sink ->
        val uid = authForUser.apply(authorization)

        if (authorizations.contains(uid)) {
            val auths = authorizations[uid]!!
            if (exist)
                auths.add(authorization)
            else
                auths.remove(authorization)
        }
        sink.success()
    }

    override fun findAuthorizationsFor(uid: T): Flux<M> = Flux.create { sink ->
        if (authorizations.contains(uid)) {
            val auths = authorizations[uid]!!
            auths.forEach { m -> sink.next(m) }
        }
        sink.complete()
    }
}