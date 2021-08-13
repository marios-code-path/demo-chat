package com.demo.chat.streams.core.persistence

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.MessagePersistence
import com.demo.chat.streams.core.CoreStreams
import com.demo.chat.streams.core.MessageSendRequest
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.context.annotation.Bean
import org.springframework.messaging.handler.annotation.SendTo
import reactor.core.publisher.Flux
import java.util.function.Function

open class MessageRequestStream<T, V, Q>(
    private val persist: MessagePersistence<T, V>,
    private val index: MessageIndexService<T, V, Q>
) {
    @Bean
    fun receiveMessageRequest() =
        Function<Flux<MessageSendRequest<T, V>>, Flux<Message<T, V>>> { msgFlux ->
            msgFlux.flatMap { req ->
                persist.key()
                    .map { key ->
                        Message.create(MessageKey.create(key.id, req.from, req.dest), req.msg, true)
                    }
                    .flatMap { msg ->
                        persist
                            .add(msg)
                            .flatMap {
                                index.add(msg)
                            }
                            .map { msg }
                    }
            }
    }
}