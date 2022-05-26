package com.demo.chat.config

import com.demo.chat.service.TopicPubSubService

interface PubSubServiceBeans<T, V> {
    fun topicExchange(): TopicPubSubService<T, V>
}