package com.demo.chat.repository.cassandra

import com.demo.chat.domain.ChatUser
import com.demo.chat.domain.ChatUserHandle
import com.demo.chat.domain.ChatUserHandleKey
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatUserRepository : ReactiveCassandraRepository<ChatUser, UUID>,
        ChatUserRepositoryCustom {
    fun findByKeyUserId(uuid: UUID): Mono<ChatUser>
}

interface ChatUserRepositoryCustom {
    fun saveUser(user: ChatUser): Mono<ChatUser>
    fun saveUsers(user: Flux<ChatUser>): Flux<ChatUser>
}

class ChatUserRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate)
    : ChatUserRepositoryCustom {
    override fun saveUser(u: ChatUser): Mono<ChatUser> =
            cassandra
                    .batchOps()
                    .insert(u)
                    .insert(ChatUserHandle(
                            ChatUserHandleKey(
                                    u.key.userId,
                                    u.key.handle
                            ),
                            u.name,
                            u.timestamp
                    ))
                    .execute()
                    .thenReturn(u)

    override fun saveUsers(users: Flux<ChatUser>): Flux<ChatUser> = users
            .flatMap {
                saveUser(it)
            }
}

interface ChatUserHandleRepository
    : ReactiveCassandraRepository<ChatUserHandle, ChatUserHandleKey> {
    fun findByKeyHandle(handle: String): Mono<ChatUserHandle>
}