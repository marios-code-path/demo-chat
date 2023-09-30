package com.demo.chat.controller.webflux.composite.mapping

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.service.composite.ChatMessageService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI

interface ChatMessageServiceRestMapping<T> : ChatMessageService<T, String> {

    @GetMapping("/topic/{id}")
    @ResponseStatus(HttpStatus.TEMPORARY_REDIRECT)
    fun listen(@ModelAttribute req: ByIdRequest<T>): Mono<String>

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
        @AuthenticationPrincipal user: ChatUserDetails<T>
    ): Mono<Void> = send(MessageSendRequest(message, user.user.key.id, id))
}