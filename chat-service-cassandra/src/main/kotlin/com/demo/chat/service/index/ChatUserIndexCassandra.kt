package com.demo.chat.service.index

import com.datastax.driver.core.policies.DefaultRetryPolicy
import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.service.ChatUserIndexService
import org.springframework.data.cassandra.core.InsertOptions
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant


class ChatUserIndexCassandra(val userHandleRepo: ChatUserHandleRepository,
                             val cassandra: ReactiveCassandraTemplate) : ChatUserIndexService {
    override fun add(ent: User, criteria: Map<String, String>): Mono<Void> =
            cassandra.insert(ChatUserHandle(
                    ChatUserHandleKey(ent.key.id, ent.key.handle),
                    ent.name,
                    ent.imageUri,
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

    override fun rem(ent: User): Mono<Void> = userHandleRepo.rem(ent.key)

    override fun findBy(query: Map<String, String>): Flux<out UserKey> =
            userHandleRepo.findByKeyHandle(query[HANDLE] ?: error(""))
                    .map {
                        it.key
                    }
                    .flux()

    companion object {
        const val NAME = "name"
        const val IMAGEURI = "imageUri"
        const val ACTIVE = "active"
        const val HANDLE = "handle"
        const val ID = "userId"
    }
}