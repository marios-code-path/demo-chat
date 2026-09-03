package com.demo.chat.service.composite.impl

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.TopicRecallRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UserRecallRequest
import com.demo.chat.service.vector.MessageRecallHit
import com.demo.chat.service.vector.MessageRecallService
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Read-only recall. Returns keys and scores only; the caller reloads full
 * messages through existing persistence. Covers only messages sent while
 * recall was active. There is no backfill.
 */
class MessageRecallServiceImpl<T>(
    private val vectorStore: VectorStore,
    private val typeUtil: TypeUtil<T>,
    private val keyType: String,
) : MessageRecallService<T> {

    override fun recallInTopic(req: TopicRecallRequest<T>): Flux<MessageRecallHit<T>> =
        // Validation runs at subscribe time. A bad request becomes an error
        // signal, not an exception from this method.
        Flux.defer {
            req.validate()
            val filter =
                "kind == 'message' && keyType == '$keyType' && topicId == '${typeUtil.toString(req.topicId)}'"
            search(req.query, req.limit, req.threshold, filter)
        }

    override fun recallByUser(req: UserRecallRequest<T>): Flux<MessageRecallHit<T>> =
        Flux.defer {
            req.validate()
            val filter =
                "kind == 'message' && keyType == '$keyType' && userId == '${typeUtil.toString(req.userId)}'"
            search(req.query, req.limit, req.threshold, filter)
        }

    override fun recallGlobal(req: GlobalRecallRequest): Flux<MessageRecallHit<T>> =
        Flux.defer {
            req.validate()
            val filter = "kind == 'message' && keyType == '$keyType'"
            search(req.query, req.limit, req.threshold, filter)
        }

    private fun search(query: String, limit: Int, threshold: Double, filter: String): Flux<MessageRecallHit<T>> {
        val builder = SearchRequest.builder()
            .query(query)
            .topK(limit)
        if (threshold == 0.0) {
            builder.similarityThresholdAll()
        } else {
            builder.similarityThreshold(threshold)
        }
        val request = builder.filterExpression(filter).build()

        return Mono.fromCallable { vectorStore.similaritySearch(request) }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany { documents -> Flux.fromIterable(documents.map { toHit(it) }) }
    }

    private fun toHit(document: Document): MessageRecallHit<T> {
        // Document id format: message:<keyType>:<messageId>.
        val messageId = document.id.substringAfterLast(':')
        val metadata = document.metadata
        return MessageRecallHit(
            key = MessageKey.create(
                typeUtil.fromString(messageId),
                typeUtil.fromString(metadata["userId"].toString()),
                typeUtil.fromString(metadata["topicId"].toString()),
            ),
            score = document.score,
        )
    }
}
