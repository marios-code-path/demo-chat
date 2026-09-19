package com.demo.chat.pubsub.kafka.impl

import com.demo.chat.domain.TypeUtil
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.ExecutionException

class KafkaTopicAdmin<T>(
    private val adminClient: AdminClient,
    private val typeUtil: TypeUtil<T>,
) {

    /**
     * Creates the topic, and answers when a broker can see it.
     *
     * **A successful createTopics does not mean the next call finds the
     * topic.** Kafka 4 removed ZooKeeper. Under KRaft the controller commits
     * the creation, and each broker then catches up from the metadata log.
     * So an admin call that lands right after the create can answer
     * UnknownTopicOrPartitionException with the message "This server does not
     * host this topic-partition".
     *
     * Measured against an embedded broker: createTopics returned with no
     * error, describeTopics and listTopics both reported the topic absent,
     * and a poll found it after 429 ms. Three topics that were never created
     * stayed absent for a full 5000 ms bound, so a negative answer is still a
     * real negative.
     *
     * **The wait belongs here and not in `exists`.** `exists` answers a real
     * negative too, and a retry there would make every true "no" pay the
     * whole timeout. A caller that opens a topic expects to pay once.
     *
     * See CHAT-ajehabnw.
     */
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
        }.then(awaitVisible(topicId))

    private fun awaitVisible(topicId: T): Mono<Void> {
        val name = kafkaTopicName(topicId)

        return Mono
            .fromCallable { adminClient.listTopics().names().get().contains(name) }
            .filter { visible -> visible }
            .repeatWhenEmpty { empties -> empties.delayElements(VISIBILITY_POLL) }
            .timeout(VISIBILITY_TIMEOUT)
            .subscribeOn(Schedulers.boundedElastic())
            .then()
    }

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

    companion object {
        /** The measured delay was 429 ms. This bound leaves room above it. */
        private val VISIBILITY_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val VISIBILITY_POLL: Duration = Duration.ofMillis(50)
    }

    // TODO: this might actaully need a lookup method. see the other implementations for clues as to this.
    private fun kafkaTopicName(topicId: T): String = typeUtil.toString(topicId)
}
