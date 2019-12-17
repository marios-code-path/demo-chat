package com.demo.chat.service.index

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.cassandra.ChatMessageTopicName
import com.demo.chat.domain.cassandra.ChatRoomNameKey
import com.demo.chat.repository.cassandra.TopicByNameRepository
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.TopicIndexService.Companion.ALL
import com.demo.chat.service.TopicIndexService.Companion.NAME
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class TopicIndexCassandra<T>(private val roomRepo: TopicRepository<T>,
                             private val nameRepo: TopicByNameRepository<T>) : TopicIndexService<T> {
    override fun add(ent: MessageTopic<T>, criteria: Map<String, String>): Mono<Void> =
            nameRepo.save(
                    ChatMessageTopicName(
                            ChatRoomNameKey(
                                    ent.key.id,
                                    ent.data),
                            true
                    )
            )
                    .then()

    override fun rem(ent: MessageTopic<T>): Mono<Void> = nameRepo.insert(ChatMessageTopicName(
            ChatRoomNameKey(ent.key.id, ent.data), false
    )).then()

    override fun findBy(query: Map<String, String>): Flux<out Key<T>> {
        val queryBy = query.keys.first()
        return when (queryBy) {
            NAME -> {
                nameRepo
                        .findByKeyName(query[NAME] ?: error("Name not valid"))
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