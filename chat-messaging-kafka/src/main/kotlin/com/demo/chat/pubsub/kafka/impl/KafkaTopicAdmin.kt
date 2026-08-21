package com.demo.chat.pubsub.kafka.impl

import com.demo.chat.domain.TypeUtil
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import reactor.core.publisher.Mono
import java.util.concurrent.ExecutionException

class KafkaTopicAdmin<T>(
    private val adminClient: AdminClient,
    private val typeUtil: TypeUtil<T>,
) {

    fun create(topicId: T): Mono<Void> =
        Mono.fromCallable {
            adminClient
                .createTopics(setOf(newTopic(topicId)))
                .all()
                .get()
        }.onErrorResume(ExecutionException::class.java) { error ->
            when (error.cause) {
                is TopicExistsException -> Mono.empty()
                else -> Mono.error(error.cause ?: error)
            }
        }.then()

    fun delete(topicId: T): Mono<Void> =
        Mono.fromCallable {
            adminClient
                .deleteTopics(listOf(kafkaTopicName(topicId)))
                .all()
                .get()
        }.onErrorResume(ExecutionException::class.java) { error ->
            when (error.cause) {
                is UnknownTopicOrPartitionException -> Mono.empty()
                else -> Mono.error(error.cause ?: error)
            }
        }.then()

    fun exists(topicId: T): Mono<Boolean> =
        Mono.fromCallable {
            adminClient
                .describeTopics(listOf(kafkaTopicName(topicId)))
                .allTopicNames()
                .get()
            true
        }.onErrorResume(ExecutionException::class.java) { error ->
            when (error.cause) {
                is UnknownTopicOrPartitionException -> Mono.just(false)
                else -> Mono.error(error.cause ?: error)
            }
        }

    private fun newTopic(topicId: T): NewTopic = NewTopic(kafkaTopicName(topicId), 1, 1.toShort())

    // TODO: this might actaully need a lookup method. see the other implementations for clues as to this.
    private fun kafkaTopicName(topicId: T): String = typeUtil.toString(topicId)
}
