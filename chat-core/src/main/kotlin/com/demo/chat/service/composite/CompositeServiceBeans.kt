package com.demo.chat.service.composite

interface CompositeServiceBeans<T, V> {

    fun messageService(): ChatMessageService<T, V>
    fun topicService(): ChatTopicService<T, V>
    fun userService(): ChatUserService<T>
}