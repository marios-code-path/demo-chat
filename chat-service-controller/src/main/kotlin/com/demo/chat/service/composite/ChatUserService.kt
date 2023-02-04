package com.demo.chat.service.composite

import com.demo.chat.domain.ByHandleRequest
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatUserService<T> {
    fun addUser(userReq: UserCreateRequest): Mono<out Key<T>>
    fun findByUsername(req: ByHandleRequest): Flux<out User<T>>
    fun findByUserId(req: ByIdRequest<T>): Mono<out User<T>>
}