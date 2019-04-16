package com.demo.chat.repository

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
    fun saveUsers(user: Flux<ChatUser>): Flux<ChatUser>
}

class ChatUserRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate)
    : ChatUserRepositoryCustom {
    override fun saveUsers(users: Flux<ChatUser>): Flux<ChatUser> = users
            .flatMap {
                cassandra
                        .batchOps()
                        .insert(it)
                        .insert(ChatUserHandle(
                                ChatUserHandleKey(
                                        it.key.userId,
                                        it.key.handle
                                ),
                                it.name,
                                it.timestamp
                        ))
                        .execute()
                        .thenReturn(it)
            }

}

interface ChatUserHandleRepository : ReactiveCassandraRepository<ChatUserHandle, ChatUserHandleKey> {
    fun findByKeyHandle(handle: String): Mono<ChatUserHandle>
}

