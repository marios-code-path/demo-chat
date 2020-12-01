package com.demo.chat.deploy.config.core

import com.demo.chat.service.PubSubTopicExchangeService

interface PubSubServiceFactory<T, V> {
    fun topicExchange(): PubSubTopicExchangeService<T, V>
}