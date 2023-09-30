package com.demo.chat.client.rsocket.clients

import com.demo.chat.client.rsocket.clients.composite.MessagingClient
import com.demo.chat.client.rsocket.clients.composite.TopicClient
import com.demo.chat.client.rsocket.clients.composite.UserClient
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.service.client.ClientFactory
import com.demo.chat.service.composite.ChatUserService
import org.springframework.messaging.rsocket.RSocketRequester

class CompositeRSocketClients<T>(
    val requesterFactory: ClientFactory<RSocketRequester>,
    val clientProperties: RSocketClientProperties
){

    fun userService(): ChatUserService<T> {
        val config = clientProperties.getServiceConfig("user").prefix!!
        val client = requesterFactory.getClientForService("user")
     return   UserClient(config, client)
    }

    fun messagingService(): MessagingClient<T, String> =
        MessagingClient(clientProperties.config["message"]?.prefix!!, requesterFactory.getClientForService("message"))

    fun topicService(): TopicClient<T, String> =
        TopicClient(clientProperties.config["topic"]?.prefix!!, requesterFactory.getClientForService("topic"))
}