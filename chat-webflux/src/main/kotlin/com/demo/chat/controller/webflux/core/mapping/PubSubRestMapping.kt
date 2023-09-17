package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.Message
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.Principal


interface TopicPubSubRestMapping<T, V> : TopicPubSubService<T, V> {

    @PostMapping("/subscribe/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun subscribeOne(@RequestParam id: T, @AuthenticationPrincipal userDetails: ChatUserDetails<T>): Mono<Void> =
        subscribe(userDetails.user.key.id, id)

    @PostMapping("/unsubscribe/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun unSubscribeOne(@ModelAttribute id: T, @AuthenticationPrincipal userDetails: ChatUserDetails<T>): Mono<Void> =
        unSubscribe(userDetails.user.key.id, id)

    @PostMapping("/unSubscribeAll")
    @ResponseStatus(HttpStatus.OK)
    fun restUnSubscribeAll(@AuthenticationPrincipal userDetails: ChatUserDetails<T>): Mono<Void> =
        unSubscribeAll(userDetails.user.key.id)

    // kick all from room
    @PostMapping("/unSubscribeAllIn/{topic}")
    @ResponseStatus(HttpStatus.OK)
    override fun unSubscribeAllIn(@RequestParam topic: T): Mono<Void>

    @PostMapping("/sendMessage")
    @ResponseStatus(HttpStatus.OK)
    override fun sendMessage(@ModelAttribute message: Message<T, V>): Mono<Void>

//    @GetMapping("/listen")
//    override fun listenTo(topic: T): Flux<out Message<T, V>>

    @GetMapping("/exists/{topic}")
    override fun exists(@RequestParam topic: T): Mono<Boolean>

    @PutMapping("/add", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    override fun open(topicId: T): Mono<Void>

    @DeleteMapping("/rem/{topicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun close(@RequestParam topicId: T): Mono<Void>

    @GetMapping("/getByUser/{uid}")
    override fun getByUser(@RequestParam uid: T): Flux<T>

    @GetMapping("/getUsersBy/{topicId}")
    override fun getUsersBy(@RequestParam topicId: T): Flux<T>
}

