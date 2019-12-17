package com.demo.chat.repository.cassandra

import com.datastax.driver.core.policies.DefaultRetryPolicy
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


interface ChatUserRepository<K> : ReactiveCassandraRepository<ChatUser<K>, K>,
        ChatUserRepositoryCustom<K> {
    fun findByKeyId(id: K): Mono<out ChatUser<K>>
    fun findByKeyIdIn(ids: Flux<K>): Flux<out ChatUser<K>>
}

interface ChatUserHandleRepository<K>
    : ReactiveCassandraRepository<ChatUserHandle<K>, K>,
        ChatUserHandleRepositoryCustom<K> {
    fun findByKeyHandle(handle: String): Mono<ChatUserHandle<K>>
}

interface ChatUserRepositoryCustom<K> {
    fun add(u: User<K>): Mono<Void>
    fun rem(key: Key<K>): Mono<Void>
}

interface ChatUserHandleRepositoryCustom<K> {
    fun add(u: User<K>): Mono<Void>
    fun rem(key: Key<K>): Mono<Void>
}

class ChatUserHandleRepositoryCustomImpl<K>(val cassandra: ReactiveCassandraTemplate)
    : ChatUserHandleRepositoryCustom<K> {
    override fun rem(key: Key<K>): Mono<Void> =
            cassandra
                    .update(Query.query(where("user_id").`is`(key.id)),
                            Update.empty().set("active", false),
                            ChatUserHandle::class.java
                    )
                    .then()

    override fun add(u: User<K>): Mono<Void> =
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

class ChatUserRepositoryCustomImpl<K>(val cassandra: ReactiveCassandraTemplate)
    : ChatUserRepositoryCustom<K> {
    override fun rem(key: Key<K>): Mono<Void> =
            cassandra
                    .update(Query.query(where("user_id").`is`(key.id)),
                            Update.empty().set("active", false),
                            ChatUser::class.java
                    )
                    .then()

    override fun add(u: User<K>): Mono<Void> =
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