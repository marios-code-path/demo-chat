package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

open class ChatUserServiceCassandra(val userRepo: ChatUserRepository,
                               val userHandleRepo: ChatUserHandleRepository)
    : ChatUserService<ChatUser, UserKey> {
    override fun authenticateUser(handle: String, password: String): Mono<ChatUserKey> =
            userHandleRepo
                    .findByKeyHandle(handle)
                    .handle { u, s ->
                        when(u) {
                            null -> s.error(UserNotFoundException)
                            else -> s.next(ChatUserKey(u.key.userId, u.key.handle))
                        }
                    }

    override fun createUser(name: String, handle: String, defaultImageUri: String): Mono<ChatUser> = userRepo
            .saveUser(ChatUser(
                    ChatUserKey(UUIDs.timeBased(), handle),
                    name,
                    defaultImageUri,
                    Instant.now()))

    override fun createUserAuthentication(uid: UUID, password: String): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getUser(handle: String): Mono<ChatUser> = userHandleRepo
            .findByKeyHandle(handle)
            .map {
                ChatUser(
                        ChatUserKey(it.key.userId, it.key.handle),
                        it.name,
                        it.imageUri,
                        it.timestamp
                )
            }

    override fun getUserById(uuid: UUID): Mono<ChatUser> = userRepo
            .findByKeyUserId(uuid)


    override fun getUsersById(uuids: Flux<UUID>): Flux<ChatUser> = userRepo
            .findByKeyUserIdIn(uuids)

}