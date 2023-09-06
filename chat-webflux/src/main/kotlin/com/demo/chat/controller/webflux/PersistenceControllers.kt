package com.demo.chat.controller.webflux

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.webflux.core.mapping.KeyValueStoreRestMapping
import com.demo.chat.controller.webflux.core.mapping.PersistenceRestMapping
import com.demo.chat.domain.*
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.core.PersistenceStore
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController



@RestController
class PersistenceRestController<T, E>(private val that: PersistenceStore<T, E>) : PersistenceRestMapping<T, E>,
    PersistenceStore<T, E> by that

@RequestMapping("/persist/user")
class UserPersistenceRestController<T, V>(s: PersistenceServiceBeans<T, V>) :
    PersistenceRestController<T, User<T>>(s.userPersistence())

@RequestMapping("/persist/message")
class MessagePersistenceRestController<T, V>(s: PersistenceServiceBeans<T, V>) :
    PersistenceRestController<T, Message<T, V>>(s.messagePersistence())

@RequestMapping("/persist/topic")
class TopicPersistenceRestController<T, V>(s: PersistenceServiceBeans<T, V>) :
    PersistenceRestController<T, MessageTopic<T>>(s.topicPersistence())

@RequestMapping("/persist/membership")
class MembershipPersistenceRestController<T, V>(s: PersistenceServiceBeans<T, V>) :
    PersistenceRestController<T, TopicMembership<T>>(s.membershipPersistence())

@RequestMapping("/persist/auth")
class AuthPersistenceRestController<T, V>(s: PersistenceServiceBeans<T, V>) :
    PersistenceRestController<T, AuthMetadata<T>>(s.authMetaPersistence())