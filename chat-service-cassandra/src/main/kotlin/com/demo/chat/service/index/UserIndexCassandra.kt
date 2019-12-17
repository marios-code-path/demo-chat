package com.demo.chat.service.index

import com.datastax.driver.core.policies.DefaultRetryPolicy
import com.demo.chat.domain.DuplicateUserException
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.cassandra.ChatUserHandle
import com.demo.chat.domain.cassandra.ChatUserHandleKey
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.service.UserIndexService
import org.springframework.data.cassandra.core.InsertOptions
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant


class UserIndexCassandra<T>(val userHandleRepo: ChatUserHandleRepository<T>,
                            val cassandra: ReactiveCassandraTemplate) : UserIndexService<T> {
    override fun add(ent: User<T>, criteria: Map<String, String>): Mono<Void> =
            cassandra.insert(ChatUserHandle(
                    ChatUserHandleKey(ent.key.id, ent.handle),
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

    override fun rem(ent: User<T>): Mono<Void> = userHandleRepo.rem(ent.key)

    override fun findBy(query: Map<String, String>): Flux<out Key<T>> =
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