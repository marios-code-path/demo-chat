package com.demo.chat.security.access.core

import com.demo.chat.domain.*
import com.demo.chat.service.core.PersistenceStore
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface PersistenceAccess<T, E : Any> : PersistenceStore<T, E> {
    @PreAuthorize("@chatAccess.hasAccessTo(#ent.key, 'PUT')")
    override fun add(ent: E): Mono<Void>
    @PreAuthorize("@chatAccess.hasAccessTo(#key, 'DEL')")
    override fun rem(key: Key<T>): Mono<Void>
    @PreAuthorize("@chatAccess.hasAccessTo(#key, 'GET')")
    override fun get(key: Key<T>): Mono<out E>
    /**
     * **A many target read answers the permitted entities alone.** Each entity
     * is evaluated on its own. A denied entity is left out, and the request does
     * not fail. See `CHAT-wkwiipgy`.
     *
     * `@PostFilter` runs inside the method security proxy, so a denied entity
     * never reaches the caller. It does reach the proxy, because the store reads
     * every key first. The reactive `@PreFilter` cannot filter [keys], because it
     * filters a `Publisher` parameter alone.
     */
    @PostFilter("@chatAccess.hasAccessTo(filterObject.key, 'GET')")
    override fun byIds(keys: List<Key<T>>): Flux<out E>
}

interface UserPersistenceAccess<T> : PersistenceStore<T, User<T>> {
    @PreAuthorize("@chatAccess.hasAccessToDomain('User', 'ALL')")
    override fun all(): Flux<out User<T>>
}

interface MessageUserPersistenceAccess<T> : PersistenceStore<T, Message<T, *>> {
    @PreAuthorize("@chatAccess.hasAccessToDomain('Message', 'ALL')")
    override fun all(): Flux<out Message<T, *>>
}

interface MessageTopicPersistenceAccess<T> : PersistenceStore<T, MessageTopic<T>> {
    @PreAuthorize("@chatAccess.hasAccessToDomain('MessageTopic', 'ALL')")
    override fun all(): Flux<out MessageTopic<T>>
}

interface KeyValuePairPersistenceAccess<T> : PersistenceStore<T, KeyValuePair<T, *>> {
    @PreAuthorize("@chatAccess.hasAccessToDomain('KeyValuePair', 'ALL')")
    override fun all(): Flux<out KeyValuePair<T, *>>
}

interface AuthMetadataUserPersistenceAccess<T> : PersistenceStore<T, AuthMetadata<T>> {
    @PreAuthorize("@chatAccess.hasAccessToDomain('AuthMetadata', 'ALL')")
    override fun all(): Flux<out AuthMetadata<T>>
}