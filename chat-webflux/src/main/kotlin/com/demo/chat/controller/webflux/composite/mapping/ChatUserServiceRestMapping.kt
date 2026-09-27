package com.demo.chat.controller.webflux.composite.mapping

import com.demo.chat.controller.webflux.resolve.Resolved
import com.demo.chat.service.core.VerifiedKey
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.*
import com.demo.chat.service.composite.ChatUserService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatUserServiceRestMapping<T> : ChatUserService<T> {

    @PostMapping("/new", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    override fun addUser(@RequestBody userReq: UserCreateRequest): Mono<out Key<T>>

    @GetMapping("/handle/{name}")
    override fun findByUsername(@ModelAttribute req: ByStringRequest): Flux<out User<T>>

    /**
     * The path id resolves in USER before the service runs. A model attribute
     * of the generic type T erased the id to text. See `CHAT-avduuqwp`, D2.
     */
    @GetMapping("/id/{id}")
    fun restFindByUserId(@Resolved(ChatDomain.USER) id: VerifiedKey<T>): Mono<out User<T>> =
        findByUserId(ByIdRequest(id.key.id))
}