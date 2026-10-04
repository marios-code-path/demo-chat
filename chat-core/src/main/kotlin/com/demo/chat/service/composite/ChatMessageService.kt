package com.demo.chat.service.composite

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageSendRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatMessageService<T, V> {
    fun listenTopic(req: ByIdRequest<T>): Flux<out Message<T, V>>

    /**
     * The stored messages of one room, and then completion.
     *
     * `listenTopic` emits the same history and then stays open for live
     * messages. A caller cannot tell where the history ends, so a list of the
     * stored messages needs this operation. See `CHAT-rghaeqsa`.
     */
    fun listMessages(req: ByIdRequest<T>): Flux<out Message<T, V>>
    fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>>
    fun send(req: MessageSendRequest<T, V>): Mono<out Key<T>>
}