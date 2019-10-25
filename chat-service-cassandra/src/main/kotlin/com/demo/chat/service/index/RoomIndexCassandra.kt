package com.demo.chat.service.index

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatRoomNameRepository
import com.demo.chat.repository.cassandra.ChatRoomRepository
import com.demo.chat.service.ChatRoomIndexService
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

class RoomIndexCassandra(private val roomRepo: ChatRoomRepository,
                         private val nameRepo: ChatRoomNameRepository) : ChatRoomIndexService {
    override fun add(key: RoomKey, criteria: Map<String, String>): Mono<Void> =
            nameRepo.save(
                    ChatRoomName(
                            ChatRoomNameKey(
                                    key.id,
                                    key.name),
                            emptySet(),
                            true,
                            Instant.now()
                    )
            )
            .then()

    override fun rem(key: RoomKey): Mono<Void> = nameRepo.insert(ChatRoomName(
            ChatRoomNameKey(key.id, key.name), setOf(), false, Instant.now()
    )).then()


    override fun findBy(query: Map<String, String>): Flux<out RoomKey> = nameRepo
            .findByKeyName(query["name"] ?: error("Name not valid"))
            .map {
                it.key
            }
            .flux()

    override fun size(roomId: EventKey): Mono<Int> = roomRepo
            .findByKeyId(roomId.id)
            .switchIfEmpty(Mono.error(RoomNotFoundException))
            .handle { it, sink ->
                when (it) {
                    null -> sink.error(RoomNotFoundException)
                    else -> {
                        var size = when (it.members) {
                            null -> 0
                            else -> it.members.size
                        }
                        sink.next(size)
                    }
                }
            }

    override fun addMember(uid: EventKey, roomId: EventKey): Mono<Void> = verifyRoom(roomId)
            .then(roomRepo.join(uid.id, roomId.id))

    override fun remMember(uid: EventKey, roomId: EventKey): Mono<Void> = verifyRoom(roomId)
            .then(roomRepo.leave(uid.id, roomId.id))

    private fun verifyRoom(roomId: EventKey): Mono<Void> = roomRepo
            .findByKeyId(roomId.id)
            .switchIfEmpty(Mono.error(RoomNotFoundException))
            .then()
}