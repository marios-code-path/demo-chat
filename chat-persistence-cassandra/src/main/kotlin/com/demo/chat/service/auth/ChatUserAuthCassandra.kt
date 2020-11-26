package com.demo.chat.service.auth

import com.demo.chat.domain.Key
import com.demo.chat.domain.UsernamePasswordAuthenticationException
import com.demo.chat.service.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

/**
 * Should do the chore of handling any authentication and authorization operations
 * using Cassandra components as the backing store
 */
@Suppress("unused")
class ChatUserAuthCassandra<T, Q>(
        private val userIndex: UserIndexService<T, Q>,
        private val passwordStore: PasswordStore<T>,
        private val userHandleToQuery: Function<String, Q>,
) : ChatAuthService<T> {

    override fun authenticate(n: String, pw: String): Mono<out Key<T>> =
            userIndex
                    .findBy(userHandleToQuery.apply(n))//mapOf(Pair(HANDLE, n)))
                    .last()
                    .flatMap { key ->
                        passwordStore
                                .getStoredCredentials(key)
                                .map {
                                    if (it.password == pw) key
                                    else null
                                }
                    }
                    .handle { key, s ->
                        when (key) {
                            null -> s.error(UsernamePasswordAuthenticationException)
                            else -> s.next(key)
                        }
                    }

    override fun createAuthentication(uid: T, pw: String): Mono<Void> =
            passwordStore.addCredential(ChatCredential(uid, pw))


    override fun authorize(uid: T, target: T, action: String): Mono<Void> = Mono.empty()

    override fun findAuthorizationsFor(uid: T): Flux<AuthorizationMeta<T>> = Flux.empty()
}