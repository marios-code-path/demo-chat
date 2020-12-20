package com.demo.chat.deploy.config.client

import com.demo.chat.service.IKeyService
import com.demo.chat.service.PubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.core.ParameterizedTypeReference
import java.util.*

open class CoreClientConfiguration<T, V, Q>(private val clients: CoreClients) {
    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name=["pubsub"])
    @Bean
    open fun pubsubClient() : PubSubService<T, V> = clients.pubsubClient(ParameterizedTypeReference.forType(UUID::class.java))

    @ConditionalOnProperty(prefix="app.client.rsocket.core", name = ["key"])
    @Bean
    open fun keyClient(): IKeyService<T> = clients.keyClient()

    @ConditionalOnProperty(prefix="app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun messagePersistenceClient() = clients.messagePersistenceClient<T, V>()

    @ConditionalOnProperty(prefix="app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun userPersistenceClient() = clients.userPersistenceClient<T>()

    @ConditionalOnProperty(prefix="app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun topicPersistenceClient() = clients.topicPersistenceClient<T>()

    @ConditionalOnProperty(prefix="app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun membershipPersistenceClient() = clients.membershipPersistenceClient<T>()

    @ConditionalOnProperty(prefix="app.client.rsocket.core", name = ["index"])
    @Bean
    open fun userIndexClient() = clients.userIndexClient<T, Q>()

    @ConditionalOnProperty(prefix="app.client.rsocket.core", name = ["index"])
    @Bean
    open fun topicIndexClient() = clients.topicIndexClient<T, Q>()

    @ConditionalOnProperty(prefix="app.client.rsocket.core", name = ["index"])
    @Bean
    open fun membershipIndexClient() = clients.membershipIndexClient<T, Q>()

    @ConditionalOnProperty(prefix="app.client.rsocket.core", name = ["index"])
    @Bean
    open fun messageIndexClient() = clients.messageIndexClient<T, V, Q>()
}