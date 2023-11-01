package com.demo.chat.controller.core.access

import com.demo.chat.domain.*
import com.demo.chat.service.core.PersistenceStore
import org.springframework.security.access.prepost.PreAuthorize
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface PersistenceAccess<T, E> : PersistenceStore<T, E> {
    @PreAuthorize("@chatAccess.hasAccessToEntity(#ent, 'PUT')")
    override fun add(ent: E): Mono<Void>
    @PreAuthorize("@chatAccess.hasAccessTo(#key, 'DEL')")
    override fun rem(key: Key<T>): Mono<Void>
    @PreAuthorize("@chatAccess.hasAccessTo(#key, 'GET')")
    override fun get(key: Key<T>): Mono<out E>
    @PreAuthorize("@chatAccess.hasAccessToMany(#keys, 'GET')")
    override fun byIds(keys: List<Key<T>>): Flux<out E> = Flux.empty()
}

interface UserPersistenceAccess<T> : PersistenceStore<T, User<T>> {
    @PreAuthorize("@chatAccess.hasAccessToDomain(T(com.demo.chat.domain.User), 'ALL')")
    override fun all(): Flux<out User<T>>
}

interface MessageUserPersistenceAccess<T> : PersistenceStore<T, Message<T, *>> {
    @PreAuthorize("@chatAccess.hasAccessToDomain(T(com.demo.chat.domain.Message), 'ALL')")
    override fun all(): Flux<out Message<T, *>>
}

interface MessageTopicPersistenceAccess<T> : PersistenceStore<T, MessageTopic<T>> {
    @PreAuthorize("@chatAccess.hasAccessToDomain(T(com.demo.chat.domain.MessageTopic), 'ALL')")
    override fun all(): Flux<out MessageTopic<T>>
}

interface KeyValuePairPersistenceAccess<T> : PersistenceStore<T, KeyValuePair<T, *>> {
    @PreAuthorize("@chatAccess.hasAccessToDomain(T(com.demo.chat.domain.KeyValuePair), 'ALL')")
    override fun all(): Flux<out KeyValuePair<T, *>>
}

interface AuthMetadataUserPersistenceAccess<T> : PersistenceStore<T, AuthMetadata<T>> {
    @PreAuthorize("@chatAccess.hasAccessToDomain(T(com.demo.chat.domain.AuthMetadata), 'ALL')")
    override fun all(): Flux<out AuthMetadata<T>>
}