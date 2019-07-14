package com.demo.chat.controllers

import com.demo.chat.UserCreateRequest
import com.demo.chat.UserRequest
import com.demo.chat.UserRequestId
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

    @MessageMapping("user-create")
    fun createNewUser(userReq: UserCreateRequest): Mono<out User> =
            userPersistence.add(userReq.name, userReq.userHandle, userReq.imgUri)

    @MessageMapping("user-handle")
    fun findByHandle(userReq: UserRequest): Mono<out User> =
            userPersistence.getByHandle(userReq.userHandle)

    @MessageMapping("user-msgId")
    fun findByUserId(userReq: UserRequestId): Mono<out User> =
            userPersistence.getById(userReq.userId)

    @MessageMapping("user-msgId-list")
    fun findByUserIdList(userReq: Flux<UserRequestId>): Flux<out User> =
            userPersistence.findByIds(userReq
                    .map {
                        it.userId
                    })
}