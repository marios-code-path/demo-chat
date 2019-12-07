package com.demo.chat.service.index

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatRoomNameRepository
import com.demo.chat.repository.cassandra.ChatRoomRepository
import com.demo.chat.service.RoomIndexService
import com.demo.chat.service.RoomIndexService.Companion.ALL
import com.demo.chat.service.RoomIndexService.Companion.NAME
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

class RoomIndexCassandra(private val roomRepo: ChatRoomRepository,
                         private val nameRepo: ChatRoomNameRepository) : RoomIndexService {
    override fun add(ent: Topic, criteria: Map<String, String>): Mono<Void> =
            nameRepo.save(
                    ChatTopicName(
                            ChatRoomNameKey(
                                    ent.key.id,
                                    ent.name),
                            true
                    )
            )
                    .then()

    override fun rem(ent: Topic): Mono<Void> = nameRepo.insert(ChatTopicName(
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