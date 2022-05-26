package com.demo.chat.client.rsocket.edge

import com.demo.chat.ByHandleRequest
import com.demo.chat.ByIdRequest
import com.demo.chat.UserCreateRequest
import com.demo.chat.domain.Key
import com.demo.chat.service.edge.ChatUserService
import com.demo.chat.domain.User
import org.springframework.core.ParameterizedTypeReference
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveFlux
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class UserClient<T>(
        private val prefix: String,
        private val requester: RSocketRequester
) : ChatUserService<T> {
    override fun addUser(userReq: UserCreateRequest): Mono<out Key<T>> = requester
            .route("${prefix}user-add")
            .data(userReq)
            .retrieveMono()

    override fun findByUsername(req: ByHandleRequest): Flux<out User<T>> = requester
            .route("${prefix}user-by-handle")
            .data(req)
            .retrieveFlux()


    override fun findByUserId(req: ByIdRequest<T>): Mono<out User<T>> = requester
            .route("${prefix}user-by-id")
            .data(req)
            .retrieveMono()
}