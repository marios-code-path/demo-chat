package com.demo.chat.index.cassandra.repository

import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.DuplicateException
import com.demo.chat.domain.Key
import com.demo.chat.index.cassandra.domain.ChatUserHandle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.cassandra.core.InsertOptions
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono


interface ChatUserHandleRepository<T : Any>
    : ReactiveCassandraRepository<ChatUserHandle<T>, T>,
    ChatUserHandleRepositoryCustom<T> {
    fun findByKeyHandle(handle: String): Mono<ChatUserHandle<T>>
    fun deleteByKeyId(id: T): Mono<Void>
}

interface ChatUserHandleRepositoryCustom<T> {
    fun add(u: ChatUserHandle<T>): Mono<Void>
    fun rem(key: Key<T>): Mono<Void>
}

class ChatUserHandleRepositoryCustomImpl<T>(val cassandra: ReactiveCassandraTemplate) :
    ChatUserHandleRepositoryCustom<T> {

    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun rem(key: Key<T>): Mono<Void> =
        cassandra
            .update(
                Query.query(where("user_id").`is`(key.id)),
                Update.empty().set("active", false),
                ChatUserHandle::class.java
            )
            .then()

    override fun add(u: ChatUserHandle<T>): Mono<Void> =
        cassandra
            .insert(
                u,
                InsertOptions.builder().withIfNotExists()
                    .consistencyLevel(ConsistencyLevel.QUORUM)
                    .build()
            )
            .doOnError { e -> logger.error(e.toString()) }
            .handle<Void> { write, sink ->
                when (write.wasApplied()) {
                    false -> sink.error(ChatException("User Index Write not applied: " + write.wasApplied()))
                    else -> sink.complete()
                }
            }
            .then()
}