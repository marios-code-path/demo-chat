package com.demo.chat.controller.app

import com.demo.chat.UserCreateRequest
import com.demo.chat.UserRequest
import com.demo.chat.UserRequestId
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.UserIndexService.Companion.HANDLE
import com.demo.chat.service.UserPersistence
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class UserController<T>(val userPersistence: UserPersistence<T>,
                             val userIndex: UserIndexService<T>) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("user-add")
    fun addUser(userReq: UserCreateRequest): Mono<T> =
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
                                .then(Mono.just(it.id))
                    }

    @MessageMapping("user-by-handle")
    fun findByHandle(userReq: UserRequest): Flux<out User<T>> = userIndex
            .findBy(mapOf(Pair(HANDLE, userReq.handle)))
            .flatMap(userPersistence::get)

    @MessageMapping("user-by-id")
    fun findByUserId(userReq: UserRequestId<T>): Mono<out User<T>> = userPersistence
            .get(Key.funKey(userReq.id))

//    @MessageMapping("user-by-ids")
//    fun findByUserIdList(userReq: Flux<UserRequestId>): Flux<out User> =
//            userPersistence.findByIds(userReq
//                    .map {
//                        it.userId
//                    })
}