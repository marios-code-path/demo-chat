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
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Each path id resolves through the registry before the service runs. See `CHAT-avduuqwp`, D2.
 *
 * **The routing annotations sit here, and not on the controller alone.** Method
 * security proxies the controller with a JDK dynamic proxy. That proxy carries
 * the interfaces and not the class, so the class level `@RequestMapping` was
 * invisible and every `/message` route answered 404. Measured on 2026-10-03.
 * `ChatTopicServiceRestMapping` met the same defect under `CHAT-znprrzhn`.
 *
 * **Each facade method carries its own check.** A facade method calls its
 * member on the same object. That call crosses no proxy, so the member's
 * `@PreAuthorize` in `MessageServiceAccess` never runs. Each check here
 * repeats the check of its member. See `CHAT-evxtlmfs`.
 */
@RestController
@RequestMapping("/message")
interface ChatMessageServiceRestMapping<T> : ChatMessageService<T, String> {

    @GetMapping("/topic/{id}", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    @PreAuthorize("@chatAccess.hasAccessToId(#id.key.id, 'SUBSCRIBE')")
    fun restListenTopic(@Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>): Flux<out Message<T, String>> =
        listenTopic(ByIdRequest(id.key.id))

    @GetMapping("/id/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@chatAccess.hasAccessToId(#id.key.id, 'GET')")
    fun restMessageById(@Resolved(ChatDomain.MESSAGE) id: VerifiedKey<T>): Mono<out Message<T, String>> =
        messageById(ByIdRequest(id.key.id))

    @PostMapping(
        "/send/{id}",
        consumes = [MediaType.TEXT_PLAIN_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@chatAccess.hasAccessToId(#id.key.id, 'SEND')")
    fun restSend(
        @Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>, @RequestBody message: String,
        @AuthenticationPrincipal details: ChatUserDetails<T>
    ): Mono<out Key<T>> = send(MessageSendRequest(message, details.user.key.id, id.key.id))
}