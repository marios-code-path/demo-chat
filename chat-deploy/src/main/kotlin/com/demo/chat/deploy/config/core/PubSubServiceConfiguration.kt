package com.demo.chat.deploy.config.core

import com.demo.chat.service.PubSubService

interface PubSubServiceConfiguration<T, V> {
    fun topicExchange(): PubSubService<T, V>
}