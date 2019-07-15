package com.demo.chat.controllers

import com.demo.chat.UserCreateRequest
import com.demo.chat.UserRequest
import com.demo.chat.UserRequestId
import com.demo.chat.domain.EventKey
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.service.ChatUserPersistence
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Controller
class UserController(val userPersistence: ChatUserPersistence<out User, UserKey>) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("user-add")
    fun addUser(userReq: UserCreateRequest): Mono<Void> =
            userPersistence
                    .key(userReq.userHandle)
                    .flatMap {
                        userPersistence
                                .add(it, userReq.name, userReq.imgUri)
                    }

    @MessageMapping("user-by-handle")
    fun findByHandle(userReq: UserRequest): Mono<out User> =
            userPersistence.getByHandle(userReq.userHandle)

    @MessageMapping("user-by-id")
    fun findByUserId(userReq: UserRequestId): Mono<out User> =
            userPersistence.getById(userReq.userId)

    @MessageMapping("user-by-ids")
    fun findByUserIdList(userReq: Flux<UserRequestId>): Flux<out User> =
            userPersistence.findByIds(userReq
                    .map {
                        it.userId
                    })
}