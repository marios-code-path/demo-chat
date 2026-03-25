package com.demo.chat.pubsub.kafka.impl

import com.demo.chat.domain.TypeUtil
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import reactor.core.publisher.Mono

class KafkaTopicAdmin<T>(
    private val adminClient: AdminClient,
    private val typeUtil: TypeUtil<T>,
) {

    fun create(topicId: T): Mono<Void> = TODO()

    fun delete(topicId: T): Mono<Void> = TODO()

    fun exists(topicId: T): Mono<Boolean> = TODO()
}
