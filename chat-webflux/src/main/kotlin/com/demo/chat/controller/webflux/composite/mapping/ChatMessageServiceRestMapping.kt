package com.demo.chat.controller.webflux.composite.mapping

import com.demo.chat.controller.webflux.resolve.Resolved
import com.demo.chat.service.core.VerifiedKey
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.composite.ChatMessageService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** Each path id resolves through the registry before the service runs. See `CHAT-avduuqwp`, D2. */
interface ChatMessageServiceRestMapping<T> : ChatMessageService<T, String> {

    @GetMapping("/topic/{id}", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    fun restListenTopic(@Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>): Flux<out Message<T, String>> =
        listenTopic(ByIdRequest(id.key.id))

    @GetMapping("/id/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    fun restMessageById(@Resolved(ChatDomain.MESSAGE) id: VerifiedKey<T>): Mono<out Message<T, String>> =
        messageById(ByIdRequest(id.key.id))

    @PostMapping(
        "/send/{id}",
        consumes = [MediaType.TEXT_PLAIN_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.CREATED)
    fun restSend(
        @Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>, @RequestBody message: String,
        @AuthenticationPrincipal details: ChatUserDetails<T>
    ): Mono<out Key<T>> = send(MessageSendRequest(message, details.user.key.id, id.key.id))
}