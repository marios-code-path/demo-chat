package com.demo.chat.config.client.rsocket

import com.demo.chat.client.rsocket.clients.CompositeRSocketClients
import com.demo.chat.client.rsocket.clients.composite.MessagingClient
import com.demo.chat.client.rsocket.clients.composite.TopicClient
import com.demo.chat.service.composite.ChatUserService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class CompositeClientsConfiguration<T>(
    val compositeClients: CompositeRSocketClients<T>
) {
    @Bean
    @ConditionalOnProperty(prefix = "app.client.rsocket.composite", name = ["user"])
    fun userService(): ChatUserService<T> = compositeClients.userService()

    @Bean
    @ConditionalOnProperty(prefix = "app.client.rsocket.composite", name = ["message"])
    fun messagingService(): MessagingClient<T, String> = compositeClients.messagingService()

    @Bean
    @ConditionalOnProperty(prefix = "app.client.rsocket.composite", name = ["topic"])
    fun topicService(): TopicClient<T, String> = compositeClients.topicService()
}