package com.demo.chat.repository.cassandra

import com.datastax.driver.core.policies.DefaultRetryPolicy
import com.demo.chat.domain.*
import com.google.common.collect.ImmutableSet
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
    fun findByKeyId(uuid: UUID): Mono<ChatUser>
    fun findByKeyIdIn(uuids: Flux<UUID>): Flux<ChatUser>
}

interface ChatUserHandleRepository
    : ReactiveCassandraRepository<ChatUserHandle, ChatUserHandleKey> {
    fun findByKeyHandle(handle: String): Mono<ChatUserHandle>
}

interface ChatUserRepositoryCustom {
    fun add(u: User): Mono<Void>
    fun rem(key: EventKey): Mono<Void>
}

class ChatUserRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate)
    : ChatUserRepositoryCustom {
    override fun rem(key: EventKey): Mono<Void> =
            cassandra
                    .update(Query.query(where("user_id").`is`(key.id)),
                            Update.empty().set("active", false),
                            ChatUser::class.java
                    )
                    .then()

    override fun add(u: User): Mono<Void> =
            cassandra
                    .insert(ImmutableSet.of(ChatUser(ChatUserKey(
                            u.key.id,
                            u.key.handle
                    ),
                            u.name,
                            u.imageUri,
                            u.timestamp)),
                            InsertOptions.builder().withIfNotExists()
                                    .retryPolicy(DefaultRetryPolicy.INSTANCE)
                                    .build()
                    )
                    .handle<Void> { write, sink ->
                        when (write.wasApplied()) {
                            false -> sink.error(DuplicateUserException)
                            else -> sink.complete()
                        }
                    }
                    .then()
}