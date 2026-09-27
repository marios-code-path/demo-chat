package com.demo.chat.config.controller.core

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.controller.resolve.KeyDomain

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.core.KeyValueStoreController
import com.demo.chat.controller.core.PersistenceServiceController
import com.demo.chat.domain.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

@Configuration
open class PersistenceControllersConfiguration {

    @Controller
    @MessageMapping("persist.user")
    @ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
    @KeyDomain(ChatDomain.USER)
    class UserPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>, v: KeyVerifier<T>) :
        PersistenceServiceController<T, User<T>>(s.userPersistence(), v)

    @Controller
    @MessageMapping("persist.message")
    @ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
    @KeyDomain(ChatDomain.MESSAGE)
    class MessagePersistenceController<T, V>(s: PersistenceServiceBeans<T, V>, v: KeyVerifier<T>) :
        PersistenceServiceController<T, Message<T, V>>(s.messagePersistence(), v)

    @Controller
    @MessageMapping("persist.topic")
    @ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
    @KeyDomain(ChatDomain.MESSAGE_TOPIC)
    class TopicPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>, v: KeyVerifier<T>) :
        PersistenceServiceController<T, MessageTopic<T>>(s.topicPersistence(), v)

    @Controller
    @MessageMapping("persist.membership")
    @ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
    @KeyDomain(ChatDomain.TOPIC_MEMBERSHIP)
    class MembershipPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>, v: KeyVerifier<T>) :
        PersistenceServiceController<T, TopicMembership<T>>(s.membershipPersistence(), v)

    @Controller
    @MessageMapping("persist.authmetadata")
    @ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
    @KeyDomain(ChatDomain.AUTH_METADATA)
    class AuthMetaPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>, v: KeyVerifier<T>) :
        PersistenceServiceController<T, AuthMetadata<T>>(s.authMetaPersistence(), v)

    @Controller
    @MessageMapping("persist.keyvalue")
    @ConditionalOnProperty(prefix = "app.controller", name = ["persistence"])
    @KeyDomain(ChatDomain.KEY_VALUE_PAIR)
    class KeyValuePersistenceController<T, V>(s: PersistenceServiceBeans<T, V>, v: KeyVerifier<T>) :
        KeyValueStoreController<T>(s.keyValuePersistence(), v)
}