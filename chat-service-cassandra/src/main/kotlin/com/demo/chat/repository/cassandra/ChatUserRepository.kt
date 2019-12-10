package com.demo.chat.repository.cassandra

import com.datastax.driver.core.policies.DefaultRetryPolicy
import com.demo.chat.domain.*
import com.demo.chat.domain.cassandra.ChatUser
import com.demo.chat.domain.cassandra.ChatUserHandle
import com.demo.chat.domain.cassandra.ChatUserHandleKey
import com.demo.chat.domain.cassandra.ChatUserKey
import org.springframework.data.cassandra.core.InsertOptions
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*


interface ChatUserRepository : ReactiveCassandraRepository<ChatUser, ChatUserKey>,
        ChatUserRepositoryCustom {
    fun findByKeyId(uuid: UUID): Mono<ChatUser>
    fun findByKeyIdIn(uuids: Flux<UUID>): Flux<ChatUser>
}

interface ChatUserHandleRepository
    : ReactiveCassandraRepository<ChatUserHandle, ChatUserHandleKey>,
        ChatUserHandleRepositoryCustom {
    fun findByKeyHandle(handle: String): Mono<ChatUserHandle>
}

interface ChatUserRepositoryCustom {
    fun add(u: User): Mono<Void>
    fun rem(key: UUIDKey): Mono<Void>
}

interface ChatUserHandleRepositoryCustom {
    fun add(u: User): Mono<Void>
    fun rem(key: UUIDKey): Mono<Void>
}

class ChatUserHandleRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate)
    : ChatUserHandleRepositoryCustom {
    override fun rem(key: UUIDKey): Mono<Void> =
            cassandra
                    .update(Query.query(where("user_id").`is`(key.id)),
                            Update.empty().set("active", false),
                            ChatUserHandle::class.java
                    )
                    .then()

    override fun add(u: User): Mono<Void> =
            cassandra
                    .insert(
                            ChatUserHandle(
                                    ChatUserHandleKey(
                                            u.key.id,
                                            u.handle
                                    ),
                                    u.name,
                                    u.imageUri,
                                    u.timestamp),
                            InsertOptions.builder().withIfNotExists()
                                    .retryPolicy(DefaultRetryPolicy.INSTANCE)
                                    .build()
                    )
                    .handle<Void> { write, sink ->
                        when (write.wasApplied()) {
                            false -> sink.error(DuplicateUserException)
                            else -> sink.complete()
                        }
                    }
                    .then()
}

class ChatUserRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate)
    : ChatUserRepositoryCustom {
    override fun rem(key: UUIDKey): Mono<Void> =
            cassandra
                    .update(Query.query(where("user_id").`is`(key.id)),
                            Update.empty().set("active", false),
                            ChatUser::class.java
                    )
                    .then()

    override fun add(u: User): Mono<Void> =
            cassandra
                    .insert(
                            ChatUser(
                                    ChatUserKey(
                                            u.key.id
                                    ),
                                    u.name,
                                    u.handle,
                                    u.imageUri,
                                    Instant.now()),
                            InsertOptions.builder().withIfNotExists()
                                    .retryPolicy(DefaultRetryPolicy.INSTANCE)
                                    .build()
                    )
                    .handle<Void> { write, sink ->
                        when (write.wasApplied()) {
                            false -> sink.error(DuplicateUserException)
                            else -> sink.complete()
                        }
                    }
                    .then()
}