package com.demo.chat.deploy.app.memory

import com.demo.chat.service.PubSubTopicExchangeService
import com.demo.chat.service.impl.memory.messaging.MemoryPubSubTopicExchange
import org.springframework.context.annotation.Bean

open class InMemoryMessagingFactory<T, V> {

    @Bean
    fun inMemoryPubSubTopicExchange(): PubSubTopicExchangeService<T, V> = MemoryPubSubTopicExchange()
}