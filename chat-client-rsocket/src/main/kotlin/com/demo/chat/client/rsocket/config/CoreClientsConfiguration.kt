package com.demo.chat.client.rsocket.config

import com.demo.chat.config.CoreServices
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class CoreClientsConfiguration<T, V, Q>(
    private val services: CoreServices<T, V, Q>
)  {
    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["pubsub"])
    @Bean
    open fun pubsubClient() = services.pubSubService()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["key"])
    @Bean
    open fun keyClient() = services.keyService()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun messagePersistenceClient() = services.messagePersistence()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun userPersistenceClient() = services.userPersistence()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun topicPersistenceClient() = services.topicPersistence()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun membershipPersistenceClient() = services.membershipPersistence()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun authMetadataPersistenceClient() = services.authMetaPersistence()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["index"])
    @Bean
    open fun userIndexClient() = services.userIndex()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["index"])
    @Bean
    open fun topicIndexClient() = services.topicIndex()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["index"])
    @Bean
    open fun membershipIndexClient() = services.membershipIndex()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["index"])
    @Bean
    open fun messageIndexClient() = services.messageIndex()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["index"])
    @Bean
    open fun authMetadataIndexClient() = services.authMetadataIndex()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["secrets"])
    @Bean
    open fun secretsClient() = services.secretsStore()
}