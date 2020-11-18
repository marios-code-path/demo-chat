package com.demo.chat.deploy.config.client

import com.demo.chat.service.IKeyService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import java.util.*

open class RSocketClientConfiguration<T, V>(private val clients: RSocketClientFactory) {

    @Profile("key-client")
    @Bean
    fun keyClient(): IKeyService<T> = clients.keyClient()

    @Profile("message-persistence-client")
    @Bean
    fun messagePersistenceClient() = clients.messageClient<T, V>()

    @Profile("user-persistence-client")
    @Bean
    fun userPersistenceClient() = clients.userClient<T>()

    @Profile("topic-persistence-client")
    @Bean
    fun topicPersistenceClient() = clients.messageTopicClient<T>()

    @Profile("membership-persistence-client")
    @Bean
    fun membershipPersistenceClient() = clients.topicMembershipClient<T>()

    @Profile("message-index-client")
    @Bean
    fun messageIndexClient() = clients.userIndexClient<T>()

    @Profile("topic-index-client")
    @Bean
    fun topicIndexClient() = clients.topicIndexClient<T>()

    @Profile("user-index-client")
    @Bean
    fun userIndexClient() = clients.userIndexClient<T>()

    @Profile("membership-index-client")
    @Bean
    fun membershipIndexClient() = clients.membershipIndexClient<T>()
}