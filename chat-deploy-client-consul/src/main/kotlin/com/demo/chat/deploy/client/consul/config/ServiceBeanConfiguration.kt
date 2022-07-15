package com.demo.chat.deploy.client.consul.config

import com.demo.chat.config.CoreServices
import com.demo.chat.service.IKeyService
import com.demo.chat.service.TopicPubSubService
import com.demo.chat.service.security.AuthMetaPersistence
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean

open class ServiceBeanConfiguration<T, V, Q>(
    private val services: CoreServices<T, V, Q>
)  {
    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["pubsub"])
    @Bean
    open fun pubsubClient() = services.topicExchange()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["key"])
    @Bean
    open fun keyClient() = services.keyService()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun messagePersistenceClient() = services.message()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun userPersistenceClient() = services.user()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun topicPersistenceClient() = services.topic()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun membershipPersistenceClient() = services.membership()

    @ConditionalOnProperty(prefix = "app.client.rsocket.core", name = ["persistence"])
    @Bean
    open fun authMetadataPersistenceClient() = services.authMetadata()

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
}