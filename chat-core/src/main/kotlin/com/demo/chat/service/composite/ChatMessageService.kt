package com.demo.chat.service.composite

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageSendRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatMessageService<T, V> {
    fun listenTopic(req: ByIdRequest<T>): Flux<out Message<T, V>>
    fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>>
    fun send(req: MessageSendRequest<T, V>): Mono<out Key<T>>
}