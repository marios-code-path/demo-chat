package com.demo.chat.service

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*


interface ChatFeedService {
    fun getFeedForUser(uid: UUID): Flux<Message<MessageKey, Any>>
    fun subscribeUser(uid: UUID, feedId: UUID): Mono<Void>
    fun unsubscribeUser(uid: UUID, feedId: UUID): Mono<Void>
    fun unsubscribeUserAll(uid: UUID): Mono<Void>
}
