package com.demo.chat.repository.cassandra

import com.demo.chat.domain.*
import org.springframework.data.cassandra.core.InsertOptions
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatUserRepository : ReactiveCassandraRepository<ChatUser, UUID>,
        ChatUserRepositoryCustom {
    fun findByKeyUserId(uuid: UUID): Mono<User>
    fun findByKeyUserIdIn(uuids: Flux<UUID>): Flux<User>
}

interface ChatUserHandleRepository
    : ReactiveCassandraRepository<ChatUserHandle, ChatUserHandleKey> {
    fun findByKeyHandle(handle: String): Mono<User>
}

interface ChatUserRepositoryCustom {
    fun saveUser(user: User): Mono<Void>
    fun rem(key: UserKey): Mono<Void>
}

class ChatUserRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate)
    : ChatUserRepositoryCustom {
    override fun rem(key: UserKey): Mono<Void> =
            cassandra
                    .batchOps()
                    .update(Query.query(where("user_id").`is`(key.id)),
                            Update.empty().set("active", false),
                            ChatRoom::class.java
                    )
                    .update(Query.query(where("room_id").`is`(key.id)),
                            Update.empty().set("active", false),
                            ChatRoomName::class.java
                    )
                    .execute()
                    .map {
                        if (!it.wasApplied())
                            throw ChatException("Cannot De-Active Room")
                    }
                    .then(
                    )

    override fun saveUser(u: User): Mono<Void> =
            cassandra
                    .batchOps()
                    .insert(ChatUser(ChatUserKey(
                            u.key.id,
                            u.key.handle
                    ),
                            u.name,
                            u.imageUri,
                            u.timestamp),
                            InsertOptions.builder().withIfNotExists().build()
                    )
                    .insert(ChatUserHandle(
                            ChatUserHandleKey(
                                    u.key.id,
                                    u.key.handle
                            ),
                            u.name, u.imageUri,
                            u.timestamp),
                            InsertOptions.builder().withIfNotExists().build()
                    )
                    .execute()
                    .handle<ChatUser> { write, sink ->
                        when (write.wasApplied()) {
                            false -> sink.error(DuplicateUserException)
                            else -> sink.complete()
                        }
                    }
                    .then()
}