package com.demo.chat.index.cassandra.impl

import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.demo.chat.domain.DuplicateException
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.index.cassandra.domain.ChatUserHandle
import com.demo.chat.index.cassandra.domain.ChatUserHandleKey
import com.demo.chat.index.cassandra.repository.ChatUserHandleRepository
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserIndexService.Companion.HANDLE
import org.springframework.data.cassandra.core.InsertOptions
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Suppress("ReactorUnusedPublisher")
class UserIndex<T>(
    private val userHandleRepo: ChatUserHandleRepository<T>,
    val cassandra: ReactiveCassandraTemplate,
) : UserIndexService<T, Map<String, String>> {
    override fun add(entity: User<T>): Mono<Void> = cassandra.insert(
        ChatUserHandle(
            ChatUserHandleKey(entity.key.id, entity.handle),
            entity.name,
            entity.imageUri,
            Instant.now()),
            InsertOptions.builder().withIfNotExists()
                    .consistencyLevel(ConsistencyLevel.LOCAL_ONE)
                    .build()
    )
            .handle { write, sink ->
                when (write.wasApplied()) {
                    false -> sink.error(DuplicateException)
                    else -> sink.complete()
                }
            }


    override fun rem(key: Key<T>): Mono<Void> = userHandleRepo.rem(key)

    override fun findBy(query: Map<String, String>): Flux<out Key<T>> =
            userHandleRepo.findByKeyHandle(query[HANDLE] ?: error("User Handle not given."))
                    .map {
                        it.key
                    }
                    .flux()

    override fun findUnique(query: Map<String, String>): Mono<out Key<T>> {
        TODO("Not yet implemented")
    }
}