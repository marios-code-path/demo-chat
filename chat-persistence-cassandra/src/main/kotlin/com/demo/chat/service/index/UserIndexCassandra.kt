package com.demo.chat.service.index

import com.datastax.driver.core.policies.DefaultRetryPolicy
import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.demo.chat.codec.Codec
import com.demo.chat.domain.DuplicateUserException
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.cassandra.ChatUserHandle
import com.demo.chat.domain.cassandra.ChatUserHandleKey
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.UserIndexService.Companion.HANDLE
import com.demo.chat.service.UserIndexService.Companion.ID
import com.demo.chat.service.UserIndexService.Companion.IMAGEURI
import com.demo.chat.service.UserIndexService.Companion.NAME
import org.springframework.data.cassandra.core.InsertOptions
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

class UserCriteriaCodec<T> : Codec<User<T>, Map<String, String>> {
    override fun decode(record: User<T>): Map<String, String> {
        return mapOf(
                Pair(ID, record.key.id.toString()),
                Pair(NAME, record.name),
                Pair(HANDLE, record.handle),
                Pair(IMAGEURI, record.imageUri)
        )
    }
}

@Suppress("ReactorUnusedPublisher")
class UserIndexCassandra<T>(
        private val criteriaCodec: Codec<User<T>, Map<String, String>>,
        private val userHandleRepo: ChatUserHandleRepository<T>,
        val cassandra: ReactiveCassandraTemplate) : UserIndexService<T> {
    override fun add(entity: User<T>): Mono<Void> =
            with(criteriaCodec.decode(entity)) {

                cassandra.insert(ChatUserHandle(
                        ChatUserHandleKey(entity.key.id, this[HANDLE]!!),
                        this[NAME]!!,
                        this[IMAGEURI]!!,
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
            }

    override fun rem(key: Key<T>): Mono<Void> = userHandleRepo.rem(key)

    override fun findBy(query: Map<String, String>): Flux<out Key<T>> =
            userHandleRepo.findByKeyHandle(query[HANDLE] ?: error("User Handle not given."))
                    .map {
                        it.key
                    }
                    .flux()
}