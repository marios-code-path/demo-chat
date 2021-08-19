package com.demo.chat.streams.functions

import com.demo.chat.domain.Message
import com.demo.chat.service.EnricherPersistenceStore
import com.demo.chat.service.MessageIndexService
import reactor.core.publisher.Flux
import java.util.function.Function

open class MessageFunctions<T, V, Q>(
    private val persist: EnricherPersistenceStore<T, MessageSendRequest<T, V>, Message<T, V>>,
    private val index: MessageIndexService<T, V, Q>
) {
    fun receiveMessageRequest() =
        Function<Flux<MessageSendRequest<T, V>>, Flux<Message<T, V>>> { msgFlux ->
            msgFlux
                .flatMap { req -> persist.addEnriched(req) }
                .flatMap { msg -> index.add(msg).thenReturn(msg) }
        }
}
