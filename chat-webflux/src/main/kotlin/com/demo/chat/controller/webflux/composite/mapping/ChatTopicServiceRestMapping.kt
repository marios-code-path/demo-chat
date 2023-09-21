package com.demo.chat.controller.webflux.composite.mapping

import com.demo.chat.domain.*
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.service.composite.ChatTopicService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatTopicServiceRestMapping<T> : ChatTopicService<T, String> {

    @PostMapping(
        "/new", consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.CREATED)
    override fun addRoom(@RequestBody req: ByStringRequest): Mono<out Key<T>>

    @GetMapping("/list", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun listRooms(): Flux<out MessageTopic<T>>

    @GetMapping("/name/{name}", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun getRoomByName(@ModelAttribute req: ByStringRequest): Mono<out MessageTopic<T>>

    @GetMapping("/members/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun roomMembers(@ModelAttribute req: ByIdRequest<T>): Mono<TopicMemberships>

    @PutMapping("/leave/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun leaveRestRoom(id: T, @AuthenticationPrincipal user: ChatUserDetails<T>): Mono<Void> =
        leaveRoom(MembershipRequest(user.user.key.id, id))

    @PutMapping("/join/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun joinRestRoom(id: T, @AuthenticationPrincipal user: ChatUserDetails<T>): Mono<Void> =
        joinRoom(MembershipRequest(user.user.key.id, id))

    @GetMapping("/id/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun getRoom(@ModelAttribute req: ByIdRequest<T>): Mono<out MessageTopic<T>>

    @DeleteMapping("/id/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun deleteRoom(@ModelAttribute req: ByIdRequest<T>): Mono<Void>

}