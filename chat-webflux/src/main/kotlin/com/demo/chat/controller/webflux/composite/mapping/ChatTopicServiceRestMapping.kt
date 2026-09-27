package com.demo.chat.controller.webflux.composite.mapping

import com.demo.chat.controller.webflux.resolve.Resolved
import com.demo.chat.service.core.VerifiedKey
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.*
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.composite.ChatTopicService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** Each path id resolves through the registry before the service runs. See `CHAT-avduuqwp`, D2. */
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
    fun restRoomMembers(@Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>): Mono<TopicMemberships> =
        roomMembers(ByIdRequest(id.key.id))

    @PutMapping("/leave/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun leaveRestRoom(@Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>, @AuthenticationPrincipal user: ChatUserDetails<T>): Mono<Void> =
        leaveRoom(MembershipRequest(user.user.key.id, id.key.id))

    @PutMapping("/join/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun joinRestRoom(@Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>, @AuthenticationPrincipal user: ChatUserDetails<T>): Mono<Void> =
        joinRoom(MembershipRequest(user.user.key.id, id.key.id))

    @GetMapping("/id/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun restGetRoom(@Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>): Mono<out MessageTopic<T>> =
        getRoom(ByIdRequest(id.key.id))

    @DeleteMapping("/id/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun restDeleteRoom(@Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>): Mono<Void> =
        deleteRoom(ByIdRequest(id.key.id))

}