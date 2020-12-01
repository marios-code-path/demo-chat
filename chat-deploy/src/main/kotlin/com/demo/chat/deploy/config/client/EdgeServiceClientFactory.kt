package com.demo.chat.deploy.config.client

import com.demo.chat.client.rsocket.edge.MessagingClient
import com.demo.chat.client.rsocket.edge.TopicClient
import com.demo.chat.client.rsocket.edge.UserClient
import com.demo.chat.deploy.config.properties.AppConfigurationProperties
import com.demo.chat.deploy.config.properties.RSocketEdgeProperties
import com.demo.chat.service.edge.ChatMessageService
import com.demo.chat.service.edge.ChatTopicService
import com.demo.chat.service.edge.ChatUserService

class EdgeServiceClientFactory(
        private val requesterFactory: RequesterFactory,
        val configProps: AppConfigurationProperties,
) {
    private val edgeProps: RSocketEdgeProperties = configProps.edge

    fun <T> userClient(): ChatUserService<T> = UserClient("${edgeProps.user.prefix}", requesterFactory.requester("user"))

    fun <T, V> messageClient(): ChatMessageService<T, V> = MessagingClient("${edgeProps.message.prefix}", requesterFactory.requester("message"))

    fun <T, V> topicClient(): ChatTopicService<T, V> = TopicClient("${edgeProps.topic.prefix}", requesterFactory.requester("topic"))
}