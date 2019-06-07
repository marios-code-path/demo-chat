package com.demo.chat.controllers

import com.demo.chat.*
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
    fun createNewUser(userReq: UserCreateRequest): Mono<UserCreateResponse> =
            userService.createUser(userReq.name, userReq.userHandle)
                    .map {
                        UserCreateResponse(it)
                    }


    @MessageMapping("user-handle")
    fun findByHandle(userReq: UserRequest): Mono<UserResponse> =
            userService.getUser(userReq.userHandle)
                    .map {
                        logger.info("The user is: $it")
                        UserResponse(it!!)
                    }

    @MessageMapping("user-id")
    fun findByUserId(userReq: UserRequestId): Mono<UserResponse> =
            userService.getUserById(userReq.userId)
                    .map {
                        UserResponse(it)
                    }

    @MessageMapping("user-id-list")
    fun findByUserIdList(userReq: Flux<UserRequestId>): Flux<UserResponse> =
            userService.getUsersById(userReq
                    .map {
                        it.userId
                    })
                    .map {
                        UserResponse(it)
                    }
}