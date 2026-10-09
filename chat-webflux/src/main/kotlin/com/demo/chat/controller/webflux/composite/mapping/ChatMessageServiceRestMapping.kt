package com.demo.chat.controller.webflux.composite.mapping

import com.demo.chat.controller.webflux.resolve.Resolved
import com.demo.chat.service.core.VerifiedKey
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.CommandStatusRequest
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.command.CommandStatus
import com.demo.chat.domain.command.MessageSendResult
import com.demo.chat.security.ChatUserDetails
import org.springframework.http.ResponseEntity
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
 * **The routing annotations sit here, and not on the controller alone.** A JDK
 * dynamic proxy carries the interfaces and not the class. Under such a proxy, a
 * class level `@RequestMapping` is invisible and every route answers 404. A
 * `@WebFluxTest` slice with method security makes a JDK proxy. Measured on
 * 2026-10-03. `ChatTopicServiceRestMapping` carries its mapping for the same
 * reason.
 *
 * **Each facade method carries its own check.** A facade method calls its
 * member on the same object. That call crosses no proxy, so the member's
 * `@PreAuthorize` in `MessageServiceAccess` never runs. Each check here
 * repeats the check of its member. See `CHAT-evxtlmfs`.
 *
 * **A real launch showed the second defect, not the first.** Before these
 * checks, a single process launch with method security answered every route.
 * A listen without `SUBSCRIBE` answered 200, and a send without `SEND` reached
 * the service. Measured on 2026-10-04. See `CHAT-oykeniec`.
 */
@RestController
@RequestMapping("/message")
interface ChatMessageServiceRestMapping<T> : ChatMessageService<T, String> {

    @GetMapping("/topic/{id}", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    @PreAuthorize("@chatAccess.hasAccessToId(#id.key.id, 'SUBSCRIBE')")
    fun restListenTopic(@Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>): Flux<out Message<T, String>> =
        listenTopic(ByIdRequest(id.key.id))

    /**
     * The stored messages of one room. The response completes after the last
     * stored message. `/topic/{id}` stays open for live messages, so it cannot
     * serve the history alone. See `CHAT-evxtlmfs`.
     *
     * The check is the `SUBSCRIBE` check of `listMessages`.
     */
    @GetMapping("/list/{id}", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    @PreAuthorize("@chatAccess.hasAccessToId(#id.key.id, 'SUBSCRIBE')")
    fun restListMessages(@Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>): Flux<out Message<T, String>> =
        listMessages(ByIdRequest(id.key.id))

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

    /**
     * The request ID travels in `Idempotency-Key`, because the body holds the
     * message text. A retry with the same key recovers the same receipt.
     * The facade carries its own check. A facade call crosses no proxy.
     */
    @PostMapping("/submit/{id}", consumes = [MediaType.TEXT_PLAIN_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("@chatAccess.hasAccessToId(#id.key.id, 'SEND')")
    fun restSubmit(
        @Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>,
        @RequestBody message: String,
        @RequestHeader("Idempotency-Key") requestId: String,
    ): Mono<ResponseEntity<MessageSendResult<T>>> =
        submit(MessageSubmitRequest(message, id.key.id, requestId))
            .map { ResponseEntity.status(SubmitStatus.of(it.outcome)).body(it) }

    /** Every caller may ask. The service answers not found for a command of another owner. */
    @GetMapping("/command/{commandId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("permitAll()")
    fun restCommandStatus(@PathVariable commandId: String): Mono<out CommandStatus<T>> =
        commandStatus(CommandStatusRequest(commandId))
}