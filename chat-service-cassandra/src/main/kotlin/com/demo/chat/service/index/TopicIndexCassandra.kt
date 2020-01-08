package com.demo.chat.service.index

import com.demo.chat.codec.Codec
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.cassandra.ChatTopicName
import com.demo.chat.domain.cassandra.ChatTopicNameKey
import com.demo.chat.repository.cassandra.TopicByNameRepository
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.TopicIndexService.Companion.ALL
import com.demo.chat.service.TopicIndexService.Companion.ID
import com.demo.chat.service.TopicIndexService.Companion.NAME
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class TopicCriteriaCodec<T>() : Codec<MessageTopic<T>, Map<String, String>> {
    override fun decode(record: MessageTopic<T>): Map<String, String> {
        return mapOf(
                Pair(ID, record.key.id.toString()),
                Pair(NAME, record.data)
        )
    }

}

class TopicIndexCassandra<T>(
        private val criteriaCodec: Codec<MessageTopic<T>, Map<String, String>>,
        private val roomRepo: TopicRepository<T>,
        private val nameRepo: TopicByNameRepository<T>) : TopicIndexService<T> {
    override fun add(ent: MessageTopic<T>): Mono<Void> =
            with(criteriaCodec.decode(ent)) {
                nameRepo.save(
                        ChatTopicName(
                                ChatTopicNameKey(
                                        ent.key.id,
                                        this[NAME] ?: ""),
                                true
                        )
                )
                        .then()
            }

    override fun rem(ent: Key<T>): Mono<Void> = nameRepo
            .delete(ChatTopicName(
                    ChatTopicNameKey(ent.id, ""), false
            ))

    override fun findBy(query: Map<String, String>): Flux<out Key<T>> {
        val queryBy = query.keys.first()
        return when (queryBy) {
            NAME -> {
                nameRepo
                        .findByKeyName(query[NAME] ?: error("Name not valid"))
                        .map { it.key }
                        .flux()
            }
            ALL -> {
                roomRepo.findAll().map { it.key }
            }
            else -> {
                Flux.empty()
            }
        }
    }
}