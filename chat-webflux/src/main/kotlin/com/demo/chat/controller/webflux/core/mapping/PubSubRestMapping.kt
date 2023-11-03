package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


interface TopicPubSubRestMapping<T> : TopicPubSubService<T, String> {

    fun keyService(): IKeyService<T>

    @PostMapping("/sub/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun subscribeOne(@PathVariable id: T, @AuthenticationPrincipal userDetails: ChatUserDetails<T>?): Mono<Void> {
        return subscribe(userDetails!!.user.key.id, id)
    }

    @DeleteMapping("/sub/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun unSubscribeOne(@PathVariable id: T, @AuthenticationPrincipal userDetails: ChatUserDetails<T>?): Mono<Void> =
        unSubscribe(userDetails!!.user.key.id, id)

    @DeleteMapping("/sub")
    @ResponseStatus(HttpStatus.OK)
    fun restUnSubscribeAll(@AuthenticationPrincipal userDetails: ChatUserDetails<T>): Mono<Void> =
        unSubscribeAll(userDetails.user.key.id)

    // kick all from room
    @DeleteMapping("/members/{topic}")
    @ResponseStatus(HttpStatus.OK)
    override fun unSubscribeAllIn(@PathVariable topic: T): Mono<Void>

    @PostMapping("/send/{topic}")
    @ResponseStatus(HttpStatus.OK)
    fun sendRestMessage(
        @PathVariable topic: T,
        @RequestBody message: String,
        @AuthenticationPrincipal user: ChatUserDetails<T>
    ): Mono<String> =
        keyService()
            .key(MessageKey::class.java)
            .flatMap { key ->
                sendMessage(
                    Message.create(
                        MessageKey.create(key.id, user.user.key.id, topic),
                        message,
                        true
                    )
                )
                    .thenReturn(key)
            }
            .map { it.id.toString() }

//    @GetMapping("/listen")
//    override fun listenTo(topic: T): Flux<out Message<T, V>>

    @GetMapping("/exists/{topic}")
    override fun exists(@PathVariable topic: T): Mono<Boolean>

    @PostMapping("/pub/{topicId}")
    @ResponseStatus(HttpStatus.CREATED)
    override fun open(@PathVariable topicId: T): Mono<Void>

    @DeleteMapping("/pub/{topicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun close(@PathVariable topicId: T): Mono<Void>

    @GetMapping("/pub/{topicId}")
    override fun getUsersBy(@PathVariable topicId: T): Flux<T>

    @GetMapping("/user/{uid}")
    override fun getByUser(@PathVariable uid: T): Flux<T>
}