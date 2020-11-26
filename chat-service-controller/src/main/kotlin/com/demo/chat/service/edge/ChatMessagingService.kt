package com.demo.chat.service.edge

import com.demo.chat.ByIdRequest
import com.demo.chat.MessageSendRequest
import com.demo.chat.domain.Message
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatMessagingService<T, V> {
    fun listenTopic(req: ByIdRequest<T>): Flux<out Message<T, V>>
    fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>>
    fun send(req: MessageSendRequest<T, V>): Mono<Void>
}