package com.demo.chat.deploy.config.controllers.core

import com.demo.chat.controller.core.PersistenceServiceController
import com.demo.chat.deploy.config.core.IndexServiceFactory
import com.demo.chat.deploy.config.core.PersistenceServiceFactory
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.MembershipPersistence
import com.demo.chat.service.MessagePersistence
import com.demo.chat.service.TopicPersistence
import com.demo.chat.service.UserPersistence
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller


open class PersistenceControllersConfiguration {

    @Controller
    @MessageMapping("persist.user")
    class UserPersistenceController<T, V>(s: PersistenceServiceFactory<T, V>) : PersistenceServiceController<T, User<T>>(s.user())

    @Controller
    @MessageMapping("persist.message")
    class MessagePersistenceController<T, V>(s: PersistenceServiceFactory<T, V>) : PersistenceServiceController<T, Message<T, V>>(s.message())

    @Controller
    @MessageMapping("persist.topic")
    class TopicPersistenceController<T, V>(s: PersistenceServiceFactory<T, V>) : PersistenceServiceController<T, MessageTopic<T>>(s.topic())

    @Controller
    @MessageMapping("persist.membership")
    class MembershipPersistenceController<T, V>(s: PersistenceServiceFactory<T, V>) : PersistenceServiceController<T, TopicMembership<T>>(s.membership())
}