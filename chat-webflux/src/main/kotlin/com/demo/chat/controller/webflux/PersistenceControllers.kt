package com.demo.chat.controller.webflux

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


open class PersistenceRestController<T, E>(private val that: PersistenceStore<T, E>) : PersistenceRestMapping<T, E>,
    PersistenceStore<T, E> by that

@RestController
@RequestMapping("/persist/user")
@ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
class UserPersistenceRestController<T, V>(s: PersistenceServiceBeans<T, V>) :
    PersistenceRestController<T, User<T>>(s.userPersistence()) {

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
class MessagePersistenceRestController<T, V>(s: PersistenceServiceBeans<T, V>) :
    PersistenceRestController<T, Message<T, V>>(s.messagePersistence()) {

    @PutMapping("/add", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun addMessage(@RequestBody req: MessageSendRequest<T, V>) = key()
        .flatMap { key ->
            add(Message.create(MessageKey.create(key.id, req.from, req.dest), req.msg, true))
                .thenReturn(key)
        }
}

@RestController
@RequestMapping("/persist/topic")
@ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
class TopicPersistenceRestController<T, V>(s: PersistenceServiceBeans<T, V>) :
    PersistenceRestController<T, MessageTopic<T>>(s.topicPersistence()) {
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
class MembershipPersistenceRestController<T, V>(s: PersistenceServiceBeans<T, V>) :
    PersistenceRestController<T, TopicMembership<T>>(s.membershipPersistence()) {
    @PutMapping("/add", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun addMembership(@RequestBody req: MembershipRequest<T>) = key()
        .flatMap { key ->
            add(TopicMembership.create(key.id, req.uid, req.roomId))
                .thenReturn(key)
        }
    }