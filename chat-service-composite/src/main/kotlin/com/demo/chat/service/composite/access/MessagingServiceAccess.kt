package com.demo.chat.service.composite.access

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.security.AccessBroker
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class MessagingServiceAccess<T, V> (
    private val authMetadataAccessBroker: AccessBroker<T>,
    private val principalPublisher: () -> Publisher<Key<T>>,
    private val that: ChatMessageService<T, V>
): ChatMessageService<T, V> {

    override fun listenTopic(req: ByIdRequest<T>): Flux<out Message<T, V>> = authMetadataAccessBroker
        .hasAccessByPrincipal(Mono.from(principalPublisher()), Key.funKey(req.id), "LISTEN")
        .thenMany(that.listenTopic(req))

    override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>> = authMetadataAccessBroker
        .hasAccessByPrincipal(Mono.from(principalPublisher()), Key.funKey(req.id), "READ")
        .then(that.messageById(req))

    override fun send(req: MessageSendRequest<T, V>): Mono<out Key<T>> = authMetadataAccessBroker
        .hasAccessByPrincipal(Mono.from(principalPublisher()), Key.funKey(req.dest), "SEND")
        .then(that.send(req))
}