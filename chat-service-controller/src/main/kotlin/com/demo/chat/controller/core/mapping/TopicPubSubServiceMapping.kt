package com.demo.chat.controller.core.mapping

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.MemberTopicRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The RSocket routes of pub/sub. Each user or member id resolves in USER, and
 * each topic id resolves in MESSAGE_TOPIC, before the service sees it. See
 * `CHAT-avduuqwp`, D10 and D11.
 *
 * A raw id of the generic type T decodes by its JSON shape, so [typeUtil]
 * converts it exactly to the key type first. A value that does not convert
 * exactly fails with `KeyInputException` before any registry read. The routes call the service methods,
 * and the service methods carry no mapping.
 */
interface TopicPubSubServiceMapping<T : Any, V> : TopicPubSubService<T, V> {
    fun verifier(): KeyVerifier<T>

    fun typeUtil(): TypeUtil<T>

    private fun user(id: Any): Mono<T> = Mono.fromCallable { typeUtil().exactFrom(id) }.flatMap { verifier().resolve(it, ChatDomain.USER) }.map { it.key.id!! }

    private fun topic(id: Any): Mono<T> = Mono.fromCallable { typeUtil().exactFrom(id) }.flatMap { verifier().resolve(it, ChatDomain.MESSAGE_TOPIC) }.map { it.key.id!! }

    @MessageMapping("subscribe")
    fun subscribeOne(req: MemberTopicRequest<T>): Mono<Void> =
        Mono.zip(user(req.member), topic(req.topic)).flatMap { subscribe(it.t1, it.t2) }

    @MessageMapping("unsubscribe")
    fun unSubscribeOne(req: MemberTopicRequest<T>): Mono<Void> =
        Mono.zip(user(req.member), topic(req.topic)).flatMap { unSubscribe(it.t1, it.t2) }

    @MessageMapping("unSubscribeAll")
    fun unSubscribeAllRoute(member: T): Mono<Void> = user(member).flatMap { unSubscribeAll(it) }

    @MessageMapping("unSubscribeAllIn")
    fun unSubscribeAllInRoute(topic: T): Mono<Void> = topic(topic).flatMap { unSubscribeAllIn(it) }

    /**
     * The message key verifies in MESSAGE. The sender resolves in USER, and the
     * destination resolves in MESSAGE_TOPIC. D10.
     */
    @MessageMapping("sendMessage")
    fun sendMessageRoute(message: Message<T, V>): Mono<Void> =
        verifier().verify(Key.of(message.key.id, message.key.root), ChatDomain.MESSAGE)
            .then(user(message.key.from))
            .then(topic(message.key.dest))
            .then(Mono.defer { sendMessage(message) })

    @MessageMapping("receiveOn")
    fun listenToRoute(topic: T): Flux<out Message<T, V>> = topic(topic).flatMapMany { listenTo(it) }

    /** An id that is not a topic answers false. */
    @MessageMapping("exists")
    fun existsRoute(topic: T): Mono<Boolean> =
        topic(topic).flatMap { exists(it) }
            .onErrorResume(KeyVerificationException::class.java) { Mono.just(false) }

    @MessageMapping("add")
    fun openRoute(topicId: T): Mono<Void> = topic(topicId).flatMap { open(it) }

    @MessageMapping("rem")
    fun closeRoute(topicId: T): Mono<Void> = topic(topicId).flatMap { close(it) }

    @MessageMapping("getByUser")
    fun getByUserRoute(uid: T): Flux<T> = user(uid).flatMapMany { getByUser(it) }

    @MessageMapping("getUsersBy")
    fun getUsersByRoute(topicId: T): Flux<T> = topic(topicId).flatMapMany { getUsersBy(it) }
}
