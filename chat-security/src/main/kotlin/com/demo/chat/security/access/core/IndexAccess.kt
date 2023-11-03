package com.demo.chat.security.access.core

import com.demo.chat.domain.*
import com.demo.chat.service.core.IndexService
import org.springframework.security.access.prepost.PreAuthorize
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface IndexAccess<T, E, Q> : IndexService<T, E, Q> {

    @PreAuthorize("@chatAccess.hasAccessToEntity(#entity, 'PUT')")
    override fun add(entity: E): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessTo(#key, 'REM')")
    override fun rem(key: Key<T>): Mono<Void>
}

interface UserIndexAccess<T, Q> : IndexAccess<T, User<T>, Q> {

    @PreAuthorize("@chatAccess.hasAccessToDomain('User', 'FIND')")
    override fun findBy(query: Q): Flux<out Key<T>>

    @PreAuthorize("@chatAccess.hasAccessToDomain('User', 'FINDUNIQUE')")
    override fun findUnique(query: Q): Mono<out Key<T>>
}

interface TopicIndexAccess<T, Q> : IndexAccess<T, MessageTopic<T>, Q> {
    @PreAuthorize("@chatAccess.hasAccessToDomain('MessageTopic', 'FIND')")
    override fun findBy(query: Q): Flux<out Key<T>>

    @PreAuthorize("@chatAccess.hasAccessToDomain('MessageTopic', 'FINDUNIQUE')")
    override fun findUnique(query: Q): Mono<out Key<T>>
}

interface MembershipIndexAccess<T, Q> : IndexAccess<T, TopicMembership<T>, Q> {
    @PreAuthorize("@chatAccess.hasAccessToDomain('TopicMembership', 'FIND')")
    override fun findBy(query: Q): Flux<out Key<T>>

    @PreAuthorize("@chatAccess.hasAccessToDomain('TopicMembership', 'FINDUNIQUE')")
    override fun findUnique(query: Q): Mono<out Key<T>>
}

interface MessageIndexAccess<T, V, Q> : IndexAccess<T, Message<T, V>, Q> {
    @PreAuthorize("@chatAccess.hasAccessToDomain('Message', 'FIND')")
    override fun findBy(query: Q): Flux<out Key<T>>

    @PreAuthorize("@chatAccess.hasAccessToDomain('Message', 'FINDUNIQUE')")
    override fun findUnique(query: Q): Mono<out Key<T>>
}

interface KeyValueIndexAccess<T, Q> : IndexAccess<T, KeyValuePair<T, Any>, Q> {
    @PreAuthorize("@chatAccess.hasAccessToDomain('KeyValuePair', 'FIND')")
    override fun findBy(query: Q): Flux<out Key<T>>

    @PreAuthorize("@chatAccess.hasAccessToDomain('KeyValuePair', 'FINDUNIQUE')")
    override fun findUnique(query: Q): Mono<out Key<T>>
}