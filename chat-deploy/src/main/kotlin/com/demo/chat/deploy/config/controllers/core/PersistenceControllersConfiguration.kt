package com.demo.chat.deploy.config.controllers.core

import com.demo.chat.controller.core.PersistenceServiceController
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
    class UserPersistenceController<T>(t: UserPersistence<T>) : PersistenceServiceController<T, User<T>>(t)

    @Controller
    @MessageMapping("persist.message")
    class MessagePersistenceController<T, E>(t: MessagePersistence<T, E>) : PersistenceServiceController<T, Message<T, E>>(t)

    @Controller
    @MessageMapping("persist.topic")
    class TopicPersistenceController<T>(t: TopicPersistence<T>) : PersistenceServiceController<T, MessageTopic<T>>(t)

    @Controller
    @MessageMapping("persist.membership")
    class MembershipPersistenceController<T>(t: MembershipPersistence<T>) : PersistenceServiceController<T, TopicMembership<T>>(t)
}