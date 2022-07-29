package com.demo.chat.init.config

import com.demo.chat.client.rsocket.config.CoreRSocketServiceDefinitions
import com.demo.chat.client.rsocket.config.DefaultRequesterFactory
import com.demo.chat.client.rsocket.config.RSocketClientProperties
import com.demo.chat.client.rsocket.config.RequesterFactory
import com.demo.chat.client.rsocket.core.SecretStoreClient
import com.demo.chat.client.rsocket.edge.MessagingClient
import com.demo.chat.client.rsocket.edge.TopicClient
import com.demo.chat.client.rsocket.edge.UserClient
import com.demo.chat.deploy.client.consul.config.ServiceBeanConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.SnowflakeGenerator
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.init.domain.AdminKey
import com.demo.chat.init.domain.AnonymousKey
import com.demo.chat.secure.config.AuthConfiguration
import com.demo.chat.secure.rsocket.TransportFactory
import com.demo.chat.service.IKeyGenerator
import com.demo.chat.service.edge.ChatUserService
import com.demo.chat.service.security.UserCredentialSecretsStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.rsocket.RSocketRequester
import java.util.*

@Configuration
class RSocketClientServiceConfiguration {

    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "uuid")
    fun uuidTypeUtil(): TypeUtil<UUID> = UUIDUtil()

    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "long")
    fun longTypeUtil(): TypeUtil<Long> = TypeUtil.LongUtil

    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "long")
    fun longKeyGen(): IKeyGenerator<Long> = SnowflakeGenerator()

    @Value("\${app.service.identity.anonymous:1}")
    private lateinit var anonymousId: String

    @Value("\${app.service.identity.admin:0}")
    private lateinit var adminId: String

    @Bean
    fun <T> anonymousKey(typeUtil: TypeUtil<T>) = AnonymousKey(typeUtil.fromString(anonymousId))

    @Bean
    fun <T> adminKey(typeUtil: TypeUtil<T>) = AdminKey(typeUtil.fromString(adminId))

    @Bean
    @ConditionalOnProperty("app.rsocket.client.requester.factory", havingValue = "default")
    fun requesterFactory(
        builder: RSocketRequester.Builder,
        clientConnectionProps: RSocketClientProperties,
        tcpConnectionFactory: TransportFactory
    ): DefaultRequesterFactory =
        DefaultRequesterFactory(
            builder,
            tcpConnectionFactory,
            clientConnectionProps.config
        )

    @Configuration
    class ServiceClientConfiguration<T>(serviceDefinitions: CoreRSocketServiceDefinitions<T, String, IndexSearchRequest>) :
        ServiceBeanConfiguration<T, String, IndexSearchRequest>(serviceDefinitions)

    @Bean
    fun <T> rSocketBoundServices(
        requesterFactory: RequesterFactory,
        clientRSocketProps: RSocketClientProperties,
        typeUtil: TypeUtil<T>
    ) = CoreRSocketServiceDefinitions<T, String, IndexSearchRequest>(
        requesterFactory,
        clientRSocketProps,
        typeUtil
    )

    @Configuration
    class EdgeConfiguration<T>(
        val clientProperties: RSocketClientProperties,
        val requesterFactory: RequesterFactory,
        val typeUtil: TypeUtil<T>
    ) {
        @Bean
        fun userService(): ChatUserService<T> =
            UserClient(clientProperties.config["user"]?.prefix!!, requesterFactory.requester("user"))

        @Bean
        fun messagingService(): MessagingClient<T, String> =
            MessagingClient(clientProperties.config["message"]?.prefix!!, requesterFactory.requester("message"))

        @Bean
        fun edgeTopicService(): TopicClient<T, String> =
            TopicClient(clientProperties.config["topic"]?.prefix!!, requesterFactory.requester("topic"))

        @Bean
        fun <T> secretsClient(): UserCredentialSecretsStore<T> =
            SecretStoreClient(clientProperties.config["secrets"]?.prefix!!, requesterFactory.requester("secrets"))
    }

    @Configuration
    class AppAuthConfiguration<T>(typeUtil: TypeUtil<T>, anonKey: AnonymousKey<T>) :
        AuthConfiguration<T>(keyTypeUtil = typeUtil, anonKey)
}