package com.demo.chat.init

import com.demo.chat.client.rsocket.config.CoreRSocketServiceDefinitions
import com.demo.chat.client.rsocket.config.DefaultRequesterFactory
import com.demo.chat.client.rsocket.config.RSocketClientProperties
import com.demo.chat.client.rsocket.config.RequesterFactory
import com.demo.chat.deploy.client.consul.config.ServiceBeanConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.SnowflakeGenerator
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.secure.config.AuthConfiguration
import com.demo.chat.secure.rsocket.TransportFactory
import com.demo.chat.service.IKeyGenerator
import com.demo.chat.service.security.SecretsStoreInMemory
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
    open fun longKeyGen(): IKeyGenerator<Long> = SnowflakeGenerator()

    @Value("\${app.service.identity.anonymous:1}")
    private lateinit var anonymousId: String

    @Value("\${app.service.identity.admin:0}")
    private lateinit var adminId: String

    @Bean
    fun <T> anonymousKey(typeUtil: TypeUtil<T>) = AnonymousKey(typeUtil.fromString(anonymousId))

    @Bean
    fun <T> adminKey(typeUtil: TypeUtil<T>) = AdminKey(typeUtil.fromString(adminId))

    @Bean
    @ConditionalOnProperty("app.rsocket.client.config.factory", havingValue = "default")
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

    @Bean
    fun <T> passwdStore(typeUtil: TypeUtil<T>): UserCredentialSecretsStore<T> = SecretsStoreInMemory()

    @Configuration
    class AppAuthConfiguration<T>(typeUtil: TypeUtil<T>, anonKey: AnonymousKey<T>) :
        AuthConfiguration<T>(keyTypeUtil = typeUtil, anonKey)

}