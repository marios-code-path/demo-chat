package com.demo.chat.repository

import com.demo.chat.domain.ChatUser
import org.springframework.data.cassandra.repository.AllowFiltering
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatUserRepository : ReactiveCassandraRepository<ChatUser, UUID> {

    @AllowFiltering
    fun findByHandle(handleQuery: String): Mono<ChatUser>

    @AllowFiltering
    fun findByName(handleQuery: String): Flux<ChatUser>

    override fun findById(uuid: UUID): Mono<ChatUser>
}