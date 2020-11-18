package com.demo.chat.deploy.config.client

import com.demo.chat.service.IKeyService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import java.util.*

open class RSocketClientConfiguration<T, V>(private val clients: RSocketClientFactory) {

    @ConditionalOnProperty(prefix="app.client.rsocket", name = ["key"])
    @Bean
    open fun keyClient(): IKeyService<T> = clients.keyClient()

    @ConditionalOnProperty(prefix="app.client.rsocket.persistence", name = ["message"])
    @Bean
    open fun messagePersistenceClient() = clients.messageClient<T, V>()

    @ConditionalOnProperty(prefix="app.client.rsocket.persistence", name = ["user"])
    @Bean
    open fun userPersistenceClient() = clients.userClient<T>()

    @ConditionalOnProperty(prefix="app.client.rsocket.persistence", name = ["topic"])
    @Bean
    open fun topicPersistenceClient() = clients.messageTopicClient<T>()

    @ConditionalOnProperty(prefix="app.client.rsocket.persistence", name = ["membership"])
    @Bean
    open fun membershipPersistenceClient() = clients.topicMembershipClient<T>()

    @ConditionalOnProperty(prefix="app.client.rsocket.index", name = ["message"])
    @Bean
    open fun messageIndexClient() = clients.userIndexClient<T>()

    @ConditionalOnProperty(prefix="app.client.rsocket.index", name = ["topic"])
    @Bean
    open fun topicIndexClient() = clients.topicIndexClient<T>()

    @ConditionalOnProperty(prefix="app.client.rsocket.index", name = ["user"])
    @Bean
    open fun userIndexClient() = clients.userIndexClient<T>()

    @ConditionalOnProperty(prefix="app.client.rsocket.index", name = ["membership"])
    @Bean
    open fun membershipIndexClient() = clients.membershipIndexClient<T>()
}