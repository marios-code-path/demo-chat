package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.service.core.TopicPubSubService
import org.mockito.BDDMockito

class TestPubSubBeans<T, V> : PubSubServiceBeans<T, V> {
    val mockedService: TopicPubSubService<T, V> = BDDMockito.mock(TopicPubSubService::class.java) as TopicPubSubService<T, V>

    override fun pubSubService(): TopicPubSubService<T, V> = mockedService

}