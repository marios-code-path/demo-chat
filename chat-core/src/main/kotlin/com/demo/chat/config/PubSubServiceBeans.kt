package com.demo.chat.config

import com.demo.chat.service.PubSubService

interface PubSubServiceBeans<T, V> {
    fun topicExchange(): PubSubService<T, V>
}