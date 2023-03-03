package com.demo.chat.controller.composite.mapping

import com.demo.chat.domain.*
import com.demo.chat.service.composite.ChatUserService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface UserServiceControllerMapping<T> : ChatUserService<T> {
    @MessageMapping("user-add")
    override fun addUser(userReq: UserCreateRequest): Mono<out Key<T>>
    @MessageMapping("user-by-handle")
    override fun findByUsername(req: ByStringRequest): Flux<out User<T>>
    @MessageMapping("user-by-id")
    override fun findByUserId(req: ByIdRequest<T>): Mono<out User<T>>
}