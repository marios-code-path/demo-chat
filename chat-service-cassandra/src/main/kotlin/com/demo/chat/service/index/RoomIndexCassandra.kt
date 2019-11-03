package com.demo.chat.service.index

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatRoomNameRepository
import com.demo.chat.repository.cassandra.ChatRoomRepository
import com.demo.chat.service.ChatRoomIndexService
import com.demo.chat.service.ChatRoomIndexService.Companion.ALL
import com.demo.chat.service.ChatRoomIndexService.Companion.NAME
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

class RoomIndexCassandra(private val roomRepo: ChatRoomRepository,
                         private val nameRepo: ChatRoomNameRepository) : ChatRoomIndexService {
    override fun add(ent: Room, criteria: Map<String, String>): Mono<Void> =
            nameRepo.save(
                    ChatRoomName(
                            ChatRoomNameKey(
                                    ent.key.id,
                                    ent.key.name),
                            emptySet(),
                            true,
                            Instant.now()
                    )
            )
                    .then()

    override fun rem(ent: Room): Mono<Void> = nameRepo.insert(ChatRoomName(
            ChatRoomNameKey(ent.key.id, ent.key.name), setOf(), false, Instant.now()
    )).then()

    override fun findBy(query: Map<String, String>): Flux<out RoomKey> {
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