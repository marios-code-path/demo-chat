package com.demo.chat.config.controller.composite

import com.demo.chat.controller.composite.mapping.MessageServiceControllerMapping
import com.demo.chat.controller.composite.mapping.TopicServiceControllerMapping
import com.demo.chat.controller.composite.mapping.UserServiceControllerMapping
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.security.access.composite.MessageServiceAccess
import com.demo.chat.security.access.composite.TopicServiceAccess
import com.demo.chat.security.access.composite.UserServiceAccess
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller


@ConditionalOnProperty(prefix = "app.controller", name = ["message"])
@Controller
@MessageMapping("message")
class MessageServiceController<T, V>(b: CompositeServiceBeans<T, V>) :
    MessageServiceControllerMapping<T, V>, MessageServiceAccess<T, V>, ChatMessageService<T, V> by b.messageService()

@ConditionalOnProperty(prefix = "app.controller", name = ["topic"])
@Controller
@MessageMapping("topic")
class TopicServiceController<T, V>(b: CompositeServiceBeans<T, V>) :
    TopicServiceControllerMapping<T, V>, TopicServiceAccess<T, V>, ChatTopicService<T, V> by b.topicService()

@ConditionalOnProperty(prefix = "app.controller", name = ["user"])
@Controller
@MessageMapping("user")
class UserServiceController<T, V>(b: CompositeServiceBeans<T, V>) :
    UserServiceControllerMapping<T>, UserServiceAccess<T>, ChatUserService<T> by b.userService()