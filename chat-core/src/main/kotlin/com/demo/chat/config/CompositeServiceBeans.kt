package com.demo.chat.config

import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.composite.ChatUserService

interface CompositeServiceBeans<T, V> {

    fun messageService(): ChatMessageService<T, V>
    fun topicService(): ChatTopicService<T, V>
    fun userService(): ChatUserService<T>
}