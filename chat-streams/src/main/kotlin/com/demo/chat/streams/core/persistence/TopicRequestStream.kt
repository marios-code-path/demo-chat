package com.demo.chat.streams.core.persistence

import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.EnricherPersistenceStore
import com.demo.chat.service.IndexService
import com.demo.chat.streams.core.MessageTopicRequest
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

open class TopicRequestStream<T, Q>(
    private val topicPersistence: EnricherPersistenceStore<T, MessageTopicRequest, MessageTopic<T>>,
    private val topicIndex: IndexService<T, MessageTopic<T>, Q>
) {
    @Bean
    open fun receiveTopicRequest() = Function<Flux<MessageTopicRequest>, Flux<MessageTopic<T>>> { msgReq ->
        msgReq.flatMap { req -> topicPersistence.addEnriched(req) }
            .flatMap { topic ->
                topicIndex
                    .add(topic)
                    .then(Mono.just(topic))
            }
    }
}