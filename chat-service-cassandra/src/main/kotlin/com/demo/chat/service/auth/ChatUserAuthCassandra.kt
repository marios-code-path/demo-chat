package com.demo.chat.service.auth

import com.demo.chat.domain.UserKey
import com.demo.chat.domain.UsernamePasswordAuthenticationException
import com.demo.chat.service.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Should do the chore of handling any authentication and authorization operations
 * using Cassandra components as the backing store
 */
class ChatUserAuthCassandra(private val userIndex: ChatUserIndexService,
                            private val passwordStore: ChatPasswordStore) : ChatAuthService<UserKey> {

    override fun authenticate(handle: String, password: String): Mono<UserKey> =
            userIndex
                    .findBy(mapOf(Pair("handle", handle)))
                    .last()
                    .flatMap { key ->
                        passwordStore
                                .getStoredCredentials(key.id)
                                .map {
                                    if (it.password == password) key
                                    else null
                                }
                    }
                    .handle { key, s ->
                        when (key) {
                            null -> s.error(UsernamePasswordAuthenticationException)
                            else -> s.next(UserKey.create(key.id, key.handle))
                        }
                    }

    override fun createAuthentication(uid: UUID, password: String): Mono<Void> =
            passwordStore.addCredential(ChatCredential(uid, password))


    override fun authorize(uid: UUID, target: UUID, action: String): Mono<Void> = Mono.empty()

    override fun findAuthorizationsFor(uid: UUID): Flux<AuthorizationMeta> = Flux.empty()
}