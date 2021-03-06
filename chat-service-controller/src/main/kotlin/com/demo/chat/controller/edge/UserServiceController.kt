package com.demo.chat.controller.edge

import com.demo.chat.ByHandleRequest
import com.demo.chat.ByIdRequest
import com.demo.chat.UserCreateRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.edge.ChatUserService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function
import java.util.stream.Collectors

open class UserServiceController<T, Q>(
        val userPersistence: PersistenceStore<T, User<T>>,
        private val userIndex: IndexService<T, User<T>, Q>,
        private val userHandleToQuery: Function<ByHandleRequest, Q>,
) : ChatUserService<T> {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("user-add")
    override fun addUser(userReq: UserCreateRequest): Mono<out Key<T>> =
            userPersistence
                    .key()
                    .flatMap {
                        val user = User.create(
                                Key.funKey(it.id),
                                userReq.name,
                                userReq.handle,
                                userReq.imgUri
                        )
                        Flux.concat(
                                userPersistence.add(user),
                                userIndex.add(user)
                        )
                                .then(Mono.just(it))
                    }

    @MessageMapping("user-by-handle")
    override fun findByHandle(req: ByHandleRequest): Flux<out User<T>> = userIndex
            .findBy(userHandleToQuery.apply(req))
            .flatMap(userPersistence::get)

    @MessageMapping("user-by-id")
    override fun findByUserId(req: ByIdRequest<T>): Mono<out User<T>> = userPersistence
            .get(Key.funKey(req.id))

    @MessageMapping("user-by-ids")
    fun findByUserIdList(userReq: List<ByIdRequest<T>>): Flux<out User<T>> =
            userPersistence.byIds(
                    userReq
                            .stream()
                            .map { Key.funKey(it.id) }
                            .collect(Collectors.toList())
            )
}