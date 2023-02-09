package com.demo.chat.deploy

import com.demo.chat.client.rsocket.RequesterFactory
import com.demo.chat.client.rsocket.clients.CompositeRSocketClients
import com.demo.chat.client.rsocket.clients.CoreRSocketClients
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies

@Configuration
@ConditionalOnProperty(prefix = "app.client", name = ["rsocket"])
@EnableConfigurationProperties(RSocketClientProperties::class)
class AppConfiguration {

    @Bean
    fun requesterBuilder(strategies: RSocketStrategies): RSocketRequester.Builder =
        RSocketRequester.builder().rsocketStrategies(strategies)

    @Bean
    fun coreClientBeans(
        requesterFactory: RequesterFactory,
        clientProps: RSocketClientProperties
    ) = CoreRSocketClients<Long, String, IndexSearchRequest>(
        requesterFactory,
        clientProps,
        TypeUtil
    )

    @Bean
    fun compositeClientBeans(
        requesterFactory: RequesterFactory,
        clientProps: RSocketClientProperties
    ) = CompositeRSocketClients<Long>(requesterFactory, clientProps)

}