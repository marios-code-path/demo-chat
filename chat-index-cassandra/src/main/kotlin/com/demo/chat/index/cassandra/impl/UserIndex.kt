package com.demo.chat.index.cassandra.impl

import com.demo.chat.index.cassandra.domain.ChatUserHandleKey

import com.demo.chat.index.cassandra.domain.ChatUserHandle

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.index.cassandra.repository.ChatUserHandleRepository
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserIndexService.Companion.HANDLE
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Suppress("ReactorUnusedPublisher")
class UserIndex<T : Any>(
    private val userHandleRepo: ChatUserHandleRepository<T>,
    private val rootKeys: RootKeys<T>,
) : UserIndexService<T, Map<String, String>> {

    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun add(entity: User<T>): Mono<Void> = userHandleRepo.add(
        ChatUserHandle(ChatUserHandleKey(entity.key.id, entity.handle), entity.name, entity.imageUri, entity.timestamp)
    )

    override fun rem(key: Key<T>): Mono<Void> = userHandleRepo.rem(key)

    override fun findBy(query: Map<String, String>): Flux<out Key<T>> =
            userHandleRepo.findByKeyHandle(query[HANDLE] ?: error("User Handle not given."))
                    .map {
                        Key.of(it.key.id, rootKeys.of(ChatDomain.USER).id)
                    }
                    .flux()

    override fun findUnique(query: Map<String, String>): Mono<out Key<T>> {
        TODO("Not yet implemented")
    }
}