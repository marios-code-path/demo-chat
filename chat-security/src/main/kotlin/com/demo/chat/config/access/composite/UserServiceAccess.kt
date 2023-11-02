package com.demo.chat.config.access.composite

import com.demo.chat.domain.*
import com.demo.chat.service.composite.ChatUserService
import org.springframework.security.access.prepost.PreAuthorize
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface UserServiceAccess<T> : ChatUserService<T> {

    @PreAuthorize("@chatAccess.hasAccessToDomain('User', 'NEW')")
    override fun addUser(userReq: UserCreateRequest): Mono<out Key<T>>

    @PreAuthorize("@chatAccess.hasAccessToDomain('User', 'FIND')")
    override fun findByUsername(req: ByStringRequest): Flux<out User<T>>

    @PreAuthorize("@chatAccess.hasAccessToDomain('User', 'FIND')")
    override fun findByUserId(req: ByIdRequest<T>): Mono<out User<T>>
}