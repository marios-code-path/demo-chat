package com.demo.chat.controllers.app

import com.demo.chat.UserCreateRequest
import com.demo.chat.UserRequest
import com.demo.chat.UserRequestId
import com.demo.chat.domain.EventKey
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.UserPersistence
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class UserController(val userPersistence: UserPersistence,
                          val userIndex: UserIndexService) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("user-add")
    fun addUser(userReq: UserCreateRequest): Mono<Void> =
            userPersistence
                    .key()
                    .flatMap {
                        val user = User.create(
                                UserKey.create(it.id, userReq.userHandle),
                                userReq.name,
                                userReq.imgUri
                        )
                        Flux.concat(
                                userPersistence.add(user),
                                userIndex.add(user, mapOf())
                        )
                                .then()
                    }

    @MessageMapping("user-by-handle")
    fun findByHandle(userReq: UserRequest): Flux<out User> = userIndex
            .findBy(mapOf(Pair("handle", userReq.userHandle)))
            .flatMap(userPersistence::get)

    @MessageMapping("user-by-id")
    fun findByUserId(userReq: UserRequestId): Mono<out User> = userPersistence
            .get(EventKey.create(userReq.userId))

//    @MessageMapping("user-by-ids")
//    fun findByUserIdList(userReq: Flux<UserRequestId>): Flux<out User> =
//            userPersistence.findByIds(userReq
//                    .map {
//                        it.userId
//                    })
}