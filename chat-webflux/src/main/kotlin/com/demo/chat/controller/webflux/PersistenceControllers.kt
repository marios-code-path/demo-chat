package com.demo.chat.controller.webflux

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.webflux.core.mapping.KeyValueStoreRestMapping
import com.demo.chat.controller.webflux.core.mapping.PersistenceRestMapping
import com.demo.chat.domain.*
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.core.PersistenceStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono


open class PersistenceRestController<T, E : Any>(
    private val that: PersistenceStore<T, E>,
    private val typeUtil: TypeUtil<T>,
    private val verifier: KeyVerifier<T>,
    private val domain: ChatDomain,
) : PersistenceRestMapping<T, E>,
    PersistenceStore<T, E> by that {

    override fun typeUtil(): TypeUtil<T> = typeUtil
    override fun verifier(): KeyVerifier<T> = verifier
    override fun domain(): ChatDomain = domain
}

@RestController
@RequestMapping("/persist/user")
@ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
class UserPersistenceRestController<T, V>(s: PersistenceServiceBeans<T, V>, typeUtil: TypeUtil<T>, verifier: KeyVerifier<T>) :
    PersistenceRestController<T, User<T>>(s.userPersistence(), typeUtil, verifier, ChatDomain.USER) {

    @PutMapping("/add", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun addUser(@RequestBody req: UserCreateRequest): Mono<Key<T>> = key()
        .flatMap { key ->
            add(User.create(key, req.name, req.handle, req.imgUri))
                .thenReturn(key)
        }
}

@RestController
@RequestMapping("/persist/message")
@ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
class MessagePersistenceRestController<T, V>(s: PersistenceServiceBeans<T, V>, typeUtil: TypeUtil<T>, verifier: KeyVerifier<T>) :
    PersistenceRestController<T, Message<T, V>>(s.messagePersistence(), typeUtil, verifier, ChatDomain.MESSAGE) {

    @PutMapping("/add", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    /**
     * The sender resolves in USER and the destination in MESSAGE_TOPIC before
     * the mint. A refused request mints no key. See `CHAT-avduuqwp`, E8.
     */
    fun addMessage(@RequestBody req: MessageSendRequest<T, V>) =
        Mono.zip(
            Mono.fromCallable { typeUtil().exactFrom(req.from as Any) }.flatMap { verifier().resolve(it, ChatDomain.USER) },
            Mono.fromCallable { typeUtil().exactFrom(req.dest as Any) }.flatMap { verifier().resolve(it, ChatDomain.MESSAGE_TOPIC) },
        )
            .flatMap { ids ->
                key().flatMap { key ->
                    add(Message.create(MessageKey.of(key.id, key.root, ids.t1.key.id, ids.t2.key.id), req.msg, true))
                        .thenReturn(key)
                }
            }
}

@RestController
@RequestMapping("/persist/topic")
@ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
class TopicPersistenceRestController<T, V>(s: PersistenceServiceBeans<T, V>, typeUtil: TypeUtil<T>, verifier: KeyVerifier<T>) :
    PersistenceRestController<T, MessageTopic<T>>(s.topicPersistence(), typeUtil, verifier, ChatDomain.MESSAGE_TOPIC) {
    @PutMapping("/add", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun addTopic(@RequestBody req: ByStringRequest) = key()
        .flatMap { key ->
            add(MessageTopic.create(key, req.name))
                .thenReturn(key)
        }
}

@RestController
@RequestMapping("/persist/membership")
@ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
class MembershipPersistenceRestController<T, V>(s: PersistenceServiceBeans<T, V>, typeUtil: TypeUtil<T>, verifier: KeyVerifier<T>) :
    PersistenceRestController<T, TopicMembership<T>>(s.membershipPersistence(), typeUtil, verifier, ChatDomain.TOPIC_MEMBERSHIP) {
    @PutMapping("/add", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    /**
     * The member resolves in USER and the room in MESSAGE_TOPIC before the
     * mint. A refused request mints no key. See `CHAT-avduuqwp`, E8.
     */
    fun addMembership(@RequestBody req: MembershipRequest<T>) =
        Mono.zip(
            Mono.fromCallable { typeUtil().exactFrom(req.uid as Any) }.flatMap { verifier().resolve(it, ChatDomain.USER) },
            Mono.fromCallable { typeUtil().exactFrom(req.roomId as Any) }.flatMap { verifier().resolve(it, ChatDomain.MESSAGE_TOPIC) },
        )
            .flatMap { ids ->
                key().flatMap { key ->
                    add(TopicMembership.create(key.id, ids.t1.key.id, ids.t2.key.id))
                        .thenReturn(key)
                }
            }
    }