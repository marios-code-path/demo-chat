package com.demo.chat.controllers

import com.demo.chat.UserCreateRequest
import com.demo.chat.UserRequest
import com.demo.chat.UserRequestId
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.service.ChatUserService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Controller
class UserController(val userService: ChatUserService<out User<UserKey>, UserKey>) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("user-create")
    fun createNewUser(userReq: UserCreateRequest): Mono<out User<UserKey>> =
            userService.createUser(userReq.name, userReq.userHandle)

    @MessageMapping("user-handle")
    fun findByHandle(userReq: UserRequest): Mono<out User<UserKey>> =
            userService.getUser(userReq.userHandle)

    @MessageMapping("user-id")
    fun findByUserId(userReq: UserRequestId): Mono<out User<UserKey>> =
            userService.getUserById(userReq.userId)

    @MessageMapping("user-id-list")
    fun findByUserIdList(userReq: Flux<UserRequestId>): Flux<out User<UserKey>> =
            userService.getUsersById(userReq
                    .map {
                        it.userId
                    })
}