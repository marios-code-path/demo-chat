package com.demo.chat.config

import com.demo.chat.service.core.TopicPubSubService

interface PubSubServiceBeans<T : Any, V> {
    fun pubSubService(): TopicPubSubService<T, V>
}