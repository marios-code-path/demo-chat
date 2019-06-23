package com.demo.chat.repository.cassandra

import com.demo.chat.domain.ChatUser
import com.demo.chat.domain.ChatUserHandle
import com.demo.chat.domain.ChatUserHandleKey
import com.demo.chat.domain.DuplicateUserException
import org.slf4j.LoggerFactory
import org.springframework.data.cassandra.core.InsertOptions
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatUserRepository : ReactiveCassandraRepository<ChatUser, UUID>,
        ChatUserRepositoryCustom {
    fun findByKeyUserId(uuid: UUID): Mono<ChatUser>
    fun findByKeyUserIdIn(uuids: Flux<UUID>): Flux<ChatUser>
}

interface ChatUserRepositoryCustom {
    fun saveUser(user: ChatUser): Mono<ChatUser>
    fun saveUsers(user: Flux<ChatUser>): Flux<ChatUser>
}

class ChatUserRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate)
    : ChatUserRepositoryCustom {

    override fun saveUser(u: ChatUser): Mono<ChatUser> =
            cassandra
                    .insert(ChatUserHandle(
                            ChatUserHandleKey(
                                    u.key.userId,
                                    u.key.handle
                            ),
                            u.name, "DEFAULT",
                            u.timestamp),
                            InsertOptions.builder().withIfNotExists().build()
                    )
                    .handle<ChatUser> { write, sink ->
                        when(write.wasApplied()) {
                            false -> sink.error( DuplicateUserException )
                            else -> sink.next(u)
                        }
                    }
                    .flatMap {
                        cassandra
                                .insert(u)
                    }

    override fun saveUsers(users: Flux<ChatUser>): Flux<ChatUser> = users
            .flatMap {
                saveUser(it)
            }
}

interface ChatUserHandleRepository
    : ReactiveCassandraRepository<ChatUserHandle, ChatUserHandleKey> {
    fun findByKeyHandle(handle: String): Mono<ChatUserHandle>
}