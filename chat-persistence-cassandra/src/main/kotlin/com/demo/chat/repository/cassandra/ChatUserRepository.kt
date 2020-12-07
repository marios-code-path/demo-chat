package com.demo.chat.repository.cassandra

import com.demo.chat.domain.DuplicateUserException
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
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


interface ChatUserRepository<T> : ReactiveCassandraRepository<ChatUser<T>, T>,
        ChatUserRepositoryCustom<T> {
    fun findByKeyId(id: T): Mono<ChatUser<T>>
    fun findByKeyIdIn(ids: List<T>): Flux<ChatUser<T>>
    fun deleteByKeyId(id: T): Mono<Void>
}

interface ChatUserHandleRepository<T>
    : ReactiveCassandraRepository<ChatUserHandle<T>, T>,
        ChatUserHandleRepositoryCustom<T> {
    fun findByKeyHandle(handle: String): Mono<ChatUserHandle<T>>
    fun deleteByKeyId(id: T): Mono<Void>
}

interface ChatUserRepositoryCustom<T> {
    fun add(u: User<T>): Mono<Void>
    fun rem(key: Key<T>): Mono<Void>
}

interface ChatUserHandleRepositoryCustom<T> {
    fun add(u: User<T>): Mono<Void>
    fun rem(key: Key<T>): Mono<Void>
}

class ChatUserHandleRepositoryCustomImpl<T>(val cassandra: ReactiveCassandraTemplate)
    : ChatUserHandleRepositoryCustom<T> {
    override fun rem(key: Key<T>): Mono<Void> =
            cassandra
                    .update(Query.query(where("user_id").`is`(key.id)),
                            Update.empty().set("active", false),
                            ChatUserHandle::class.java
                    )
                    .then()

    override fun add(u: User<T>): Mono<Void> =
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
                            InsertOptions.builder().withIfNotExists().build()
                    )
                    .handle<Void> { write, sink ->
                        when (write.wasApplied()) {
                            false -> sink.error(DuplicateUserException)
                            else -> sink.complete()
                        }
                    }
                    .then()
}

class ChatUserRepositoryCustomImpl<T>(val cassandra: ReactiveCassandraTemplate)
    : ChatUserRepositoryCustom<T> {
    override fun rem(key: Key<T>): Mono<Void> =
            cassandra
                    .update(Query.query(where("user_id").`is`(key.id)),
                            Update.empty().set("active", false),
                            ChatUser::class.java
                    )
                    .then()

    override fun add(u: User<T>): Mono<Void> =
            cassandra
                    .insert(
                            ChatUser(
                                    ChatUserKey(
                                            u.key.id
                                    ),
                                    u.name,
                                    u.handle,
                                    u.imageUri,
                                    Instant.now())
//                            InsertOptions
//                                    .builder()
//                                    .withIfNotExists()
//                                    .consistencyLevel(ConsistencyLevel.ANY)
//                                    .build()
                    )
//                    .handle<Void> { write, sink ->
//                        when (write.wasApplied()) {
//                            false -> sink.error(DuplicateUserException)
//                            else -> sink.complete()
//                        }
//                    }
                    .then()
}