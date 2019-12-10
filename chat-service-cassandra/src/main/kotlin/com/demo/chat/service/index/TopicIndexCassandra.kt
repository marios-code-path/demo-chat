package com.demo.chat.service.index

import com.demo.chat.domain.*
import com.demo.chat.domain.cassandra.ChatEventTopicName
import com.demo.chat.domain.cassandra.ChatRoomNameKey
import com.demo.chat.repository.cassandra.TopicByNameRepository
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.TopicIndexService.Companion.ALL
import com.demo.chat.service.TopicIndexService.Companion.NAME
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class TopicIndexCassandra(private val roomRepo: TopicRepository,
                          private val nameRepo: TopicByNameRepository) : TopicIndexService {
    override fun add(ent: EventTopic, criteria: Map<String, String>): Mono<Void> =
            nameRepo.save(
                    ChatEventTopicName(
                            ChatRoomNameKey(
                                    ent.key.id,
                                    ent.name),
                            true
                    )
            )
                    .then()

    override fun rem(ent: EventTopic): Mono<Void> = nameRepo.insert(ChatEventTopicName(
            ChatRoomNameKey(ent.key.id, ent.name), false
    )).then()

    override fun findBy(query: Map<String, String>): Flux<out TopicKey> {
        val queryBy = query.keys.first()
        return when (queryBy) {
            NAME -> {
                nameRepo
                        .findByKeyName(query[NAME] ?: error("Name not valid"))
                        .map {
                            it.key
                        }
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