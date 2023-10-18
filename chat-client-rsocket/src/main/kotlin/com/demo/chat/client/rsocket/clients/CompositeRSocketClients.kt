package com.demo.chat.client.rsocket.clients

import com.demo.chat.client.rsocket.clients.composite.MessagingClient
import com.demo.chat.client.rsocket.clients.composite.TopicClient
import com.demo.chat.client.rsocket.clients.composite.UserClient
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.domain.ChatException
import com.demo.chat.service.client.ClientFactory
import com.demo.chat.service.composite.ChatUserService
import org.springframework.messaging.rsocket.RSocketRequester

class CompositeRSocketClients<T, V>(
    val requesterFactory: ClientFactory<RSocketRequester>,
    val clientProperties: RSocketClientProperties
): CompositeServiceBeans<T, V> {

    override fun userService(): ChatUserService<T> {
        var config = clientProperties.getServiceConfig("user").prefix

        if(config == null)
            throw ChatException("client service configuration for user not found.")

        val client = requesterFactory.getClientForService("user")

        return UserClient(config, client)
    }

    override fun messageService(): MessagingClient<T, V> =
        MessagingClient(clientProperties.config["message"]?.prefix!!, requesterFactory.getClientForService("message"))

    override fun topicService(): TopicClient<T, V> =
        TopicClient(clientProperties.config["topic"]?.prefix!!, requesterFactory.getClientForService("topic"))
}