package com.demo.chat.streams.core.persistence

import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.TopicPersistence
import com.demo.chat.streams.core.CoreStreams
import com.demo.chat.streams.core.MessageTopicRequest
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Flux
import java.util.function.Function

class TopicRequestStream<T, Q>(
    private val topicPersistence: TopicPersistence<T>,
    private val topicIndex: TopicIndexService<T, Q>
) {
    @Bean
    fun receiveMessageTopicRequest() = Function<Flux<MessageTopicRequest>, Flux<MessageTopic<T>>> { msgReq ->
        msgReq.flatMap { req ->
            topicPersistence.key()
                .map { key -> MessageTopic.create(key, req.name) }
        }
            .flatMap { topic ->
                topicIndex.add(topic).map { topic }
            }
    }
}