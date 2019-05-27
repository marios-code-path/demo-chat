package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.ChatUser
import com.demo.chat.domain.ChatUserKey
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

class ChatUserServiceCassandra(val userRepo: ChatUserRepository,
                               val userHandleRepo: ChatUserHandleRepository) : ChatUserService<ChatUser, ChatUserKey> {
    override fun authenticateUser(name: String, password: String): Mono<ChatUserKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createUser(name: String, handle: String): Mono<ChatUserKey> = userRepo
            .saveUser(ChatUser(
                    ChatUserKey(UUIDs.timeBased(), handle),
                    name,
                    Instant.now()))
            .map {
                it.key
            }

    override fun createUserAuthentication(uid: UUID, password: String): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getUser(handle: String): Mono<ChatUser> = userHandleRepo
            .findByKeyHandle(handle)
            .map {
                ChatUser(
                        ChatUserKey(it.key.userId, it.key.handle),
                        it.name,
                        it.timestamp
                )
            }

    override fun getUserById(uuid: UUID): Mono<ChatUser> = userRepo
            .findByKeyUserId(uuid)

    override fun getUsersById(uuids: Flux<UUID>): Flux<ChatUser> = userRepo
            .findByKeyUserIdIn(uuids)

}