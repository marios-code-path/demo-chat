package com.demo.chat.config.client.rsocket

import com.demo.chat.client.rsocket.RequesterFactory
import com.demo.chat.client.rsocket.clients.CompositeRSocketClients
import com.demo.chat.client.rsocket.clients.CoreRSocketClients
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies

@Configuration
@ConditionalOnProperty("app.client.protocol", havingValue = "rsocket")
@EnableConfigurationProperties(RSocketClientProperties::class)
class ClientConfiguration {

    @Bean
    fun requesterBuilder(strategies: RSocketStrategies): RSocketRequester.Builder =
        RSocketRequester.builder().rsocketStrategies(strategies)

    @Bean
    fun <T>coreClientBeans(
        requesterFactory: RequesterFactory,
        clientProps: RSocketClientProperties,
        typeUtil: TypeUtil<T>
    ) = CoreRSocketClients<T, String, IndexSearchRequest>(
        requesterFactory,
        clientProps,
        typeUtil
    )

    @Bean
    fun <T> compositeClientBeans(
        requesterFactory: RequesterFactory,
        clientProps: RSocketClientProperties
    ) = CompositeRSocketClients<T>(requesterFactory, clientProps)

}