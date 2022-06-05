package com.demo.chat.deploy.config.client

import com.demo.chat.config.CoreClientBeans
import com.demo.chat.service.IKeyService
import com.demo.chat.service.TopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.core.ParameterizedTypeReference

open class AppClientBeansConfiguration<T, V, Q>(
    private val clients: CoreClientBeans<T, V, Q>,
    private val keyType: ParameterizedTypeReference<T>
) {
    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["pubsub"])
    @Bean
    open fun pubsubClient(): TopicPubSubService<T, V> = clients.topicExchange()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["key"])
    @Bean
    open fun keyClient(): IKeyService<T> = clients.keyService()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun messagePersistenceClient() = clients.message()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun userPersistenceClient() = clients.user()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun topicPersistenceClient() = clients.topic()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun membershipPersistenceClient() = clients.membership()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["index"])
    @Bean
    open fun userIndexClient() = clients.userIndex()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["index"])
    @Bean
    open fun topicIndexClient() = clients.topicIndex()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["index"])
    @Bean
    open fun membershipIndexClient() = clients.membershipIndex()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["index"])
    @Bean
    open fun messageIndexClient() = clients.messageIndex()
}