package com.demo.chat.service

import com.demo.chat.domain.UserKey
import com.demo.chat.domain.UserNotFoundException
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Should do the chore of handling any authentication and authorization operations
 * using Cassandra components as the backing store
 */
class ChatAuthCassandra(val userIndex: ChatUserIndexCassandra,
                        val passwordStore: ChatPasswordStore) : ChatAuthService<UserKey> {

    override fun authenticate(handle: String, password: String): Mono<UserKey> =
            userIndex
                    .findBy(mapOf(Pair("handle", handle)))
                    .last()
                    .handle { key, s ->
                        when (key) {
                            null -> s.error(UserNotFoundException)
                            else -> s.next(UserKey.create(key.id, key.handle))
                        }
                    }

    override fun createAuthentication(uid: UUID, password: String): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun authorize(uid: UUID, target: UUID, action: String): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findAuthorizationsFor(uid: UUID): Flux<AuthorizationMeta> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}