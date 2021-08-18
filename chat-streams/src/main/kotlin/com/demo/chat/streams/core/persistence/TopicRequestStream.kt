package com.demo.chat.streams.core.persistence

import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.streams.core.MessageTopicRequest
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

open class TopicRequestStream<T, Q>(
    private val topicPersistence: PersistenceStore<T, MessageTopic<T>>,
    private val topicIndex: IndexService<T, MessageTopic<T>, Q>
) {
    @Bean
    open fun receiveTopicRequest() = Function<Flux<MessageTopicRequest>, Flux<MessageTopic<T>>> { msgReq ->
        msgReq.flatMap { req ->
            topicPersistence.key()
                .map { key -> MessageTopic.create(key, req.name) }
        }
            .flatMap { topic ->
                Flux.concat(topicPersistence.add(topic), topicIndex.add(topic))
                    .then(Mono.just(topic))
            }
    }
}