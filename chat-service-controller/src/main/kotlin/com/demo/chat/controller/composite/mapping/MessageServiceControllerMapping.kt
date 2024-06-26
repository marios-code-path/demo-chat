package com.demo.chat.controller.composite.mapping

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.Message
import com.demo.chat.service.composite.ChatMessageService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface MessageServiceControllerMapping<T, V> : ChatMessageService<T, V> {
    @MessageMapping("message-listen-topic")
    override fun listenTopic(req: ByIdRequest<T>): Flux<out Message<T, V>>
    @MessageMapping("message-by-id")
    override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>>
    @MessageMapping("message-send")
    override fun send(req: MessageSendRequest<T, V>): Mono<out Key<T>>
}