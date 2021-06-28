package com.demo.chat.controller.edge.mapping

import com.demo.chat.ByHandleRequest
import com.demo.chat.ByIdRequest
import com.demo.chat.UserCreateRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.edge.ChatUserService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatUserServiceMapping<T> : ChatUserService<T> {
    @MessageMapping("user-add")
    override fun addUser(userReq: UserCreateRequest): Mono<out Key<T>>
    @MessageMapping("user-by-handle")
    override fun findByHandle(req: ByHandleRequest): Flux<out User<T>>
    @MessageMapping("user-by-id")
    override fun findByUserId(req: ByIdRequest<T>): Mono<out User<T>>
}