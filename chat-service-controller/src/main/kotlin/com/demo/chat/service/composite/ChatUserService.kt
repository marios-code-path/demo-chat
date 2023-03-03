package com.demo.chat.service.composite

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatUserService<T> {
    fun addUser(userReq: UserCreateRequest): Mono<out Key<T>>
    fun findByUsername(req: ByStringRequest): Flux<out User<T>>
    fun findByUserId(req: ByIdRequest<T>): Mono<out User<T>>
}