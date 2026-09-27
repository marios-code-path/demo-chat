package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.controller.webflux.resolve.Resolved
import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.service.core.VerifiedKey
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The REST routes of pub/sub. Each path id resolves through the registry
 * before the service runs. See `CHAT-avduuqwp`, D2.
 *
 * The routes call the service methods, and the service methods carry no
 * mapping. A path variable of the generic type T erased to text, so these
 * routes passed text ids to the service before this change.
 */
interface TopicPubSubRestMapping<T : Any> : TopicPubSubService<T, String> {

    fun keyService(): IKeyService<T>

    @PostMapping("/sub/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun subscribeOne(
        @Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>,
        @AuthenticationPrincipal userDetails: ChatUserDetails<T>?,
    ): Mono<Void> = subscribe(userDetails!!.user.key.id, id.key.id)

    @DeleteMapping("/sub/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun unSubscribeOne(
        @Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>,
        @AuthenticationPrincipal userDetails: ChatUserDetails<T>?,
    ): Mono<Void> = unSubscribe(userDetails!!.user.key.id, id.key.id)

    @DeleteMapping("/sub")
    @ResponseStatus(HttpStatus.OK)
    fun restUnSubscribeAll(@AuthenticationPrincipal userDetails: ChatUserDetails<T>): Mono<Void> =
        unSubscribeAll(userDetails.user.key.id)

    // kick all from room
    @DeleteMapping("/members/{topic}")
    @ResponseStatus(HttpStatus.OK)
    fun restUnSubscribeAllIn(@Resolved(ChatDomain.MESSAGE_TOPIC) topic: VerifiedKey<T>): Mono<Void> =
        unSubscribeAllIn(topic.key.id)

    @PostMapping("/send/{topic}")
    @ResponseStatus(HttpStatus.OK)
    fun sendRestMessage(
        @Resolved(ChatDomain.MESSAGE_TOPIC) topic: VerifiedKey<T>,
        @RequestBody message: String,
        @AuthenticationPrincipal user: ChatUserDetails<T>
    ): Mono<String> =
        keyService()
            .key(ChatDomain.MESSAGE)
            .flatMap { key ->
                sendMessage(
                    Message.create(
                        MessageKey.of(key.id, key.root, user.userId(), topic.key.id),
                        message,
                        true
                    )
                )
                    .thenReturn(key)
            }
            .map { it.id.toString() }

    @GetMapping("/listen/{topic}", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    fun restListenTo(@Resolved(ChatDomain.MESSAGE_TOPIC) topic: VerifiedKey<T>): Flux<out Message<T, String>> =
        listenTo(topic.key.id)

    @GetMapping("/exists/{topic}")
    fun restExists(@Resolved(ChatDomain.MESSAGE_TOPIC) topic: VerifiedKey<T>): Mono<Boolean> =
        exists(topic.key.id)

    @PostMapping("/pub/{topicId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun restOpen(@Resolved(ChatDomain.MESSAGE_TOPIC) topicId: VerifiedKey<T>): Mono<Void> = open(topicId.key.id)

    @DeleteMapping("/pub/{topicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun restClose(@Resolved(ChatDomain.MESSAGE_TOPIC) topicId: VerifiedKey<T>): Mono<Void> = close(topicId.key.id)

    @GetMapping("/pub/{topicId}")
    fun restGetUsersBy(@Resolved(ChatDomain.MESSAGE_TOPIC) topicId: VerifiedKey<T>): Flux<T> =
        getUsersBy(topicId.key.id)

    @GetMapping("/user/{uid}")
    fun restGetByUser(@Resolved(ChatDomain.USER) uid: VerifiedKey<T>): Flux<T> = getByUser(uid.key.id)
}
