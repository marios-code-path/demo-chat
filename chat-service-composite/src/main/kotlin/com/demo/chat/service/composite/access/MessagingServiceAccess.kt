package com.demo.chat.service.composite.access

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.CommandStatusRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.command.CommandStatus
import com.demo.chat.domain.command.MessageSendResult
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.security.AccessBroker
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class MessagingServiceAccess<T, V> (
    private val authMetadataAccessBroker: AccessBroker<T>,
    private val principalPublisher: () -> Publisher<Key<T>>,
    private val that: ChatMessageService<T, V>,
    private val verifier: KeyVerifier<T>,
): ChatMessageService<T, V> {

    override fun listenTopic(req: ByIdRequest<T>): Flux<out Message<T, V>> = verifier.resolve(req.id, ChatDomain.MESSAGE_TOPIC)
        .flatMap { authMetadataAccessBroker.hasAccessByPrincipal(Mono.from(principalPublisher()), it, "LISTEN") }
        .thenMany(that.listenTopic(req))

    override fun listMessages(req: ByIdRequest<T>): Flux<out Message<T, V>> = verifier.resolve(req.id, ChatDomain.MESSAGE_TOPIC)
        .flatMap { authMetadataAccessBroker.hasAccessByPrincipal(Mono.from(principalPublisher()), it, "LISTEN") }
        .thenMany(that.listMessages(req))

    override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>> = verifier.resolve(req.id, ChatDomain.MESSAGE)
        .flatMap { authMetadataAccessBroker.hasAccessByPrincipal(Mono.from(principalPublisher()), it, "READ") }
        .then(that.messageById(req))

    override fun send(req: MessageSendRequest<T, V>): Mono<out Key<T>> = verifier.resolve(req.dest, ChatDomain.MESSAGE_TOPIC)
        .flatMap { authMetadataAccessBroker.hasAccessByPrincipal(Mono.from(principalPublisher()), it, "SEND") }
        .then(that.send(req))

    override fun submit(req: MessageSubmitRequest<T, V>): Mono<out MessageSendResult<T>> = verifier.resolve(req.dest, ChatDomain.MESSAGE_TOPIC)
        .flatMap { authMetadataAccessBroker.hasAccessByPrincipal(Mono.from(principalPublisher()), it, "SEND") }
        .then(that.submit(req))

    override fun commandStatus(req: CommandStatusRequest): Mono<out CommandStatus<T>> = that.commandStatus(req)
}