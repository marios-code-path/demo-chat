package com.demo.chat.service.index

import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.demo.chat.domain.DuplicateUserException
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.cassandra.ChatUserHandle
import com.demo.chat.domain.cassandra.ChatUserHandleKey
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.UserIndexService.Companion.HANDLE
import org.springframework.data.cassandra.core.InsertOptions
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@Suppress("ReactorUnusedPublisher")
class UserIndexCassandra<T>(
        private val userHandleRepo: ChatUserHandleRepository<T>,
        val cassandra: ReactiveCassandraTemplate,
) : UserIndexService<T, Map<String, String>> {
    override fun add(entity: User<T>): Mono<Void> = cassandra.insert(ChatUserHandle(
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
                    false -> sink.error(DuplicateUserException)
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