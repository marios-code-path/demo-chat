package com.demo.chat.service.composite.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.service.vector.MessageDocumentMapper
import com.demo.chat.service.vector.MessageVectorIndexer
import org.springframework.ai.vectorstore.VectorStore
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Bridges the blocking VectorStore to the send chain. Every Spring AI call
 * runs on Schedulers.boundedElastic(). record == false (join and leave
 * alerts) returns success without a write.
 */
class VectorStoreMessageVectorIndexer<T>(
    private val vectorStore: VectorStore,
    private val mapper: MessageDocumentMapper<T>,
) : MessageVectorIndexer<T> {

    override fun add(message: Message<T, String>): Mono<Void> =
        if (!message.record) {
            Mono.empty()
        } else {
            Mono.fromCallable {
                vectorStore.add(listOf(mapper.toDocument(message)))
            }
                .subscribeOn(Schedulers.boundedElastic())
                .then()
        }

    override fun remove(key: Key<T>): Mono<Void> =
        Mono.fromCallable {
            vectorStore.delete(listOf(mapper.documentId(key.id)))
        }
            .subscribeOn(Schedulers.boundedElastic())
            .then()
}
