package com.demo.chat.security.access.composite

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.service.composite.ChatMessageService
import org.springframework.security.access.prepost.PreAuthorize
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface MessageServiceAccess<T, V> : ChatMessageService<T, V> {

    @PreAuthorize("@chatAccess.hasAccessToId(#req.id, 'SUBSCRIBE')")
    override fun listenTopic(req: ByIdRequest<T>): Flux<out Message<T, V>>

    /**
     * **A list reads what a listen reads, so it takes the same check.** The
     * shipped `GET` row on the `MessageTopic` root reaches every caller, so a
     * `GET` check would show every room to every user. The owner chose
     * `SUBSCRIBE` on 2026-10-03. See `CHAT-rghaeqsa`.
     */
    @PreAuthorize("@chatAccess.hasAccessToId(#req.id, 'SUBSCRIBE')")
    override fun listMessages(req: ByIdRequest<T>): Flux<out Message<T, V>>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.id, 'GET')")
    override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>>

    @PreAuthorize("@chatAccess.hasAccessToId(#req.dest, 'SEND')")
    override fun send(req: MessageSendRequest<T, V>): Mono<out Key<T>>
}