package com.demo.chat.security.access.composite

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.service.composite.ChatMessageService
import org.springframework.security.access.prepost.PreAuthorize
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface MessageServiceAccess<T, V> : ChatMessageService<T, V> {

    @PreAuthorize("@chatAccess.hasAccessTo(#req.component1(), 'SUBSCRIBE')")
    override fun listenTopic(req: ByIdRequest<T>): Flux<out Message<T, V>>

    @PreAuthorize("@chatAccess.hasAccessTo(#req.component1(), 'GET')")
    override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>>

    @PreAuthorize("@chatAccess.hasAccessTo(#req.dest(), 'SEND')")
    override fun send(req: MessageSendRequest<T, V>): Mono<Void>
}