package com.demo.chat.controller.webflux.composite.mapping

import com.demo.chat.domain.*
import com.demo.chat.service.composite.ChatUserService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatUserServiceRestMapping<T> : ChatUserService<T> {

    @PostMapping("/add", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    override fun addUser(@RequestBody userReq: UserCreateRequest): Mono<out Key<T>>

    @GetMapping("/handle/{name}")
    override fun findByUsername(@ModelAttribute req: ByStringRequest): Flux<out User<T>>

    @GetMapping("/id/{id}")
    override fun findByUserId(@ModelAttribute req: ByIdRequest<T>): Mono<out User<T>>
}