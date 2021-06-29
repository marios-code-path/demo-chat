package com.demo.chat.service.impl.memory.auth

import com.demo.chat.domain.Key
import com.demo.chat.domain.UsernamePasswordAuthenticationException
import com.demo.chat.service.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import java.util.function.Function

class AuthenticationServiceDefault<T, E, V>(
    private val userIndex: IndexService<T, E, Map<String, E>>,
    private val passwordStore: PasswordStore<T, V>,
    private val passwordValidator: BiFunction<V, V, Boolean>
) : AuthenticationService<T, E, V> {
    override fun createAuthentication(uid: T, pw: V): Mono<Void> = passwordStore.addCredential(Key.funKey(uid), pw)

    override fun authenticate(n: E, pw: V): Mono<out Key<T>> =
        userIndex.findUnique(mapOf(Pair(UserIndexService.HANDLE, n)))
            .flatMap { userKey ->
                passwordStore
                    .getStoredCredentials(userKey)
                    .map { secure ->
                        if (!passwordValidator.apply(pw, secure)) throw UsernamePasswordAuthenticationException
                        userKey
                    }
            }
}

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