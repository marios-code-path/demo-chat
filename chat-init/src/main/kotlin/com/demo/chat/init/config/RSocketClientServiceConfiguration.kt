package com.demo.chat.init.config

import com.demo.chat.client.rsocket.RequesterFactory
import com.demo.chat.client.rsocket.clients.CoreRSocketClients
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.SnowflakeGenerator
import com.demo.chat.domain.TypeUtil
import com.demo.chat.init.domain.AdminKey
import com.demo.chat.init.domain.AnonymousKey
import com.demo.chat.config.secure.AuthConfiguration
import com.demo.chat.service.core.IKeyGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*
import java.util.function.Supplier

@Configuration
class RSocketClientServiceConfiguration {

    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "long")
    fun longKeyGen(): IKeyGenerator<Long> = SnowflakeGenerator()

    @Value("\${app.service.identity.anonymous:1}")
    private lateinit var anonymousId: String

    @Value("\${app.service.identity.admin:0}")
    private lateinit var adminId: String

    @Bean
    fun <T> anonymousKey(typeUtil: TypeUtil<T>) = Supplier { AnonymousKey(typeUtil.fromString(anonymousId)) }

    @Bean
    fun <T> adminKey(typeUtil: TypeUtil<T>) = Supplier { AdminKey(typeUtil.fromString(adminId)) }

    @Bean
    fun <T: Any> rSocketBoundServices(
        requesterFactory: RequesterFactory,
        clientRSocketProps: RSocketClientProperties,
        typeUtil: TypeUtil<T>
    ) = CoreRSocketClients<T, String, IndexSearchRequest>(
        requesterFactory,
        clientRSocketProps,
        typeUtil
    )

    @Configuration
    class AppAuthConfiguration<T: Any>(typeUtil: TypeUtil<T>, anonKey: Supplier<AnonymousKey<T>>) :
        AuthConfiguration<T>(keyTypeUtil = typeUtil, anonKey)
}