package com.demo.chat.service

import com.demo.chat.domain.ChatUserHandle
import com.demo.chat.domain.ChatUserHandleKey
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

class ChatUserIndexCassandra(val userHandleRepo: ChatUserHandleRepository,
                             val cassandra: ReactiveCassandraTemplate) : ChatIndexService<UserKey, Map<String, String>> {
    override fun add(key: UserKey, criteria: Map<String, String>): Mono<Void> =
            userHandleRepo
                    .insert(ChatUserHandle(
                            ChatUserHandleKey(key.id, key.handle),
                            criteria["name"] ?: error(""),
                            criteria["defaultImageUri"] ?: error(""),
                            Instant.now())
                    )
                    .then()

    override fun rem(key: UserKey): Mono<Void> = cassandra
            .update(Query.query(where("user_id").`is`(key.id), where("handle").`is`(key.handle)),
                    Update.empty().set("active", false),
                    ChatUserHandle::class.java
            )
            .then()

    override fun findBy(query: Map<String, String>): Flux<UserKey> =
            userHandleRepo
                    .findByKeyHandle(query["handle"] ?: error(""))
                    .map {
                        UserKey.create(it.key.id, it.key.handle)
                    }
                    .flux()
}