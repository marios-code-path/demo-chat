package com.demo.chat.secure

import com.demo.chat.ByIdRequest
import com.demo.chat.MessageSendRequest
import com.demo.chat.domain.Message
import com.demo.chat.service.edge.ChatMessageService
import org.springframework.security.core.context.SecurityContextHolder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class ChatMessagingServiceSecurity <T, V>: ChatMessageService <T, V>{
    // Requires access-check by topic
    // before topic get called
    override fun listenTopic(req: ByIdRequest<T>): Flux<out Message<T, V>> {
        SecurityContextHolder.getContext().authentication.principal
        TODO("Not yet implemented")
    }

    override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>> {
        TODO("Not yet implemented")
    }

    override fun send(req: MessageSendRequest<T, V>): Mono<Void> {
        TODO("Not yet implemented")
    }
}