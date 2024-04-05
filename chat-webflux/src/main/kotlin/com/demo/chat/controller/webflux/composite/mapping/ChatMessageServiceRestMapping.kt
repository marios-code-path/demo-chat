package com.demo.chat.controller.webflux.composite.mapping

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

interface ChatMessageServiceRestMapping<T> : ChatMessageService<T, String> {

    @GetMapping("/topic/{id}", produces = [MediaType.APPLICATION_NDJSON_VALUE])
    override fun listenTopic(@ModelAttribute req: ByIdRequest<T>): Flux<out Message<T, String>>

    @GetMapping("/id/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, String>>

    @PostMapping(
        "/send/{id}",
        consumes = [MediaType.TEXT_PLAIN_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.CREATED)
    fun restSend(
        @PathVariable id: T, @RequestBody message: String,
        @AuthenticationPrincipal details: ChatUserDetails<T>
    ): Mono<out Key<T>> = send(MessageSendRequest(message, details.user.key.id, id))
}