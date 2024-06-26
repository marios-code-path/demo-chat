package com.demo.chat.client.rsocket.clients.composite

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.domain.Message
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveFlux
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class MessagingClient<T, V>(
        private val prefix: String,
        private val requester: RSocketRequester
) : ChatMessageService<T, V> {
    override fun listenTopic(req: ByIdRequest<T>): Flux<out Message<T, V>> =
            requester
                    .route("${prefix}message-listen-topic")
                    .data(req)
                    .retrieveFlux()

    override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>> =
            requester
                    .route("${prefix}message-by-id")
                    .data(req)
                    .retrieveMono()

    override fun send(req: MessageSendRequest<T, V>): Mono<out Key<T>> =
            requester
                    .route("${prefix}message-send")
                    .data(req)
                    .retrieveMono()
}