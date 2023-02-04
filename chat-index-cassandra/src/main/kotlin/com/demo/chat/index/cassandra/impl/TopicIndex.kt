package com.demo.chat.index.cassandra.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.index.cassandra.domain.ChatTopicName
import com.demo.chat.index.cassandra.domain.ChatTopicNameKey
import com.demo.chat.index.cassandra.repository.TopicByNameRepository
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.TopicIndexService.Companion.NAME
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

// TODO need a more idiomatic way of obtaining ALL
class TopicIndex<T>(
        private val nameRepo: TopicByNameRepository<T>
) : TopicIndexService<T, Map<String, String>> {
    override fun add(entity: MessageTopic<T>): Mono<Void> = nameRepo.save(
            ChatTopicName(
                    ChatTopicNameKey(
                            entity.key.id,
                            entity.data),
                    true
            )
    )
            .then()


    override fun rem(key: Key<T>): Mono<Void> = nameRepo
            .delete(
                ChatTopicName(
                    ChatTopicNameKey(key.id, ""), false
            )
            )

    override fun findBy(query: Map<String, String>): Flux<out Key<T>> {
        return when (query.keys.first()) {
            NAME -> {
                nameRepo
                        .findByKeyName(query[NAME] ?: error("Topic Name not found"))
                        .map { it.key }
                        .flux()
            }
            else -> {
                Flux.empty()
            }
        }
    }

    override fun findUnique(query: Map<String, String>): Mono<out Key<T>> {
        TODO("Not yet implemented")
    }
}