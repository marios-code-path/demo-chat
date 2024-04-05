package com.demo.chat.service.composite.impl

import com.demo.chat.domain.*
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.core.TopicPubSubService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

open class MessagingServiceImpl<T, V, Q>(
    private val messageIndex: MessageIndexService<T, V, Q>,
    private val messagePersistence: MessagePersistence<T, V>,
    private val pubsub: TopicPubSubService<T, V>,
    private val topicIdToQuery: Function<ByIdRequest<T>, Q>,
) : ChatMessageService<T, V> {

    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun listenTopic(req: ByIdRequest<T>): Flux<out Message<T, V>> =
        Flux.concat(
            messageIndex
                .findBy(topicIdToQuery.apply(req))
                .collectList()
                .flatMapMany { messageKeys ->
                    messagePersistence.byIds(messageKeys)
                },
            pubsub.listenTo(req.id)
        )

    override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>> =
        messagePersistence
            .get(Key.funKey(req.id))

    override fun send(req: MessageSendRequest<T, V>): Mono<out Key<T>> {
        val sending: (T) -> Message<T, V> = {
            Message.create(MessageKey.create(it, req.from, req.dest), req.msg, true)
        }

        return messagePersistence
            .key()
            .flatMap { messageKey ->
                val keyId = messageKey.id

                Flux.concat(
                    messagePersistence.add(sending(keyId)),
                    messageIndex.add(sending(keyId)),
                    pubsub.sendMessage(sending(keyId))
                )
                    .then(Mono.just(messageKey))
            }
    }
}