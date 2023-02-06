package com.demo.chat.config

import com.demo.chat.service.core.TopicPubSubService

interface PubSubServiceBeans<T, V> {
    fun pubSubService(): TopicPubSubService<T, V>
}