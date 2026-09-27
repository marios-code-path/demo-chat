package com.demo.chat.service.composite.impl

import com.demo.chat.domain.JobRecord
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.service.vector.JobRecordCodec
import com.demo.chat.service.vector.JobRecordWriter
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Writes one job record through the three services a job record needs.
 *
 * It never calls MessagingServiceImpl.send(). That method also calls
 * MessageVectorIndexer.add(), so a job record would enter vector recall, and a
 * later rebuild would read its own output back from MessagePersistence.all().
 *
 * A failed step stops the steps after it and keeps the steps before it. So
 * persistence can hold a record that the topic index cannot discover. The same
 * rule already governs MessagingServiceImpl.send().
 */
class ComposedJobRecordWriter<T : Any, V, Q>(
    private val messagePersistence: MessagePersistence<T, V>,
    private val messageIndex: MessageIndexService<T, V, Q>,
    private val pubsub: TopicPubSubService<T, V>,
    private val codec: JobRecordCodec,
    private val asValue: (String) -> V,
) : JobRecordWriter<T> {

    override fun write(record: JobRecord<T>): Mono<Void> {
        val message = Message.create(
            // The record key was minted in the message store, so it keeps its root.
            // The destination is the job topic, not the job key. See CHAT-avduuqwp, D2.
            MessageKey.of(record.key.id, record.key.root, record.workerKey.id, record.topicKey.id),
            asValue(codec.encode(record)),
            true,
        )

        return Flux.concat(
            Mono.defer { messagePersistence.add(message) },
            Mono.defer { messageIndex.add(message) },
            Mono.defer { pubsub.sendMessage(message) },
        ).then()
    }
}
