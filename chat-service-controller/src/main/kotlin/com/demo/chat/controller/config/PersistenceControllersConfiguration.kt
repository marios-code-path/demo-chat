package com.demo.chat.controller.config

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.core.PersistenceServiceController
import com.demo.chat.controller.core.mapping.PersistenceStoreMapping
import com.demo.chat.domain.*
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.security.AuthMetaPersistence
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller


open class PersistenceControllersConfiguration {

    @Controller
    @MessageMapping("persist.user")
    class UserPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, User<T>>(s.user())

    @Controller
    @MessageMapping("persist.message")
    class MessagePersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, Message<T, V>>(s.message())

    @Controller
    @MessageMapping("persist.topic")
    class TopicPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, MessageTopic<T>>(s.topic())

    @Controller
    @MessageMapping("persist.membership")
    class MembershipPersistenceController<T, V>(s: PersistenceServiceBeans<T, V>) :
        PersistenceServiceController<T, TopicMembership<T>>(s.membership())

    @Controller
    @MessageMapping("persist.authmetadata")
    class AuthMetaPersistenceController<T>(that: AuthMetaPersistence<T>) :
        PersistenceStoreMapping<T, AuthMetadata<T>>,
        PersistenceStore<T, AuthMetadata<T>> by that
}