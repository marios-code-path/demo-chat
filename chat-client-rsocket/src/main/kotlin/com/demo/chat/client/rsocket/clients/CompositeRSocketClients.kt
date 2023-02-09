package com.demo.chat.client.rsocket.clients

import com.demo.chat.client.rsocket.RequesterFactory
import com.demo.chat.client.rsocket.clients.composite.MessagingClient
import com.demo.chat.client.rsocket.clients.composite.TopicClient
import com.demo.chat.client.rsocket.clients.composite.UserClient
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.service.composite.ChatUserService

class CompositeRSocketClients<T>(
    val requesterFactory: RequesterFactory,
    val clientProperties: RSocketClientProperties
){
    fun userService(): ChatUserService<T> =
        UserClient(clientProperties.config["user"]?.prefix!!, requesterFactory.requester("user"))

    fun messagingService(): MessagingClient<T, String> =
        MessagingClient(clientProperties.config["message"]?.prefix!!, requesterFactory.requester("message"))

    fun topicService(): TopicClient<T, String> =
        TopicClient(clientProperties.config["topic"]?.prefix!!, requesterFactory.requester("topic"))
}