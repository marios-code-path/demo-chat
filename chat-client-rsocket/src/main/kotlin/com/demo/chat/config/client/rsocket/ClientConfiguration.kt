package com.demo.chat.config.client.rsocket

import com.demo.chat.client.rsocket.DefaultRequesterFactory
import com.demo.chat.client.rsocket.RequesterFactory
import com.demo.chat.client.rsocket.clients.CompositeRSocketClients
import com.demo.chat.client.rsocket.clients.CoreRSocketClients
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.client.ClientFactory
import com.demo.chat.service.client.transport.ClientTransportFactory
import io.rsocket.transport.ClientTransport
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies

@Configuration
@ConditionalOnProperty("app.client.protocol", havingValue = "rsocket")
class ClientConfiguration {

    @Bean
    @ConditionalOnProperty("app.rsocket.client.requester.factory", havingValue = "default")
    fun requesterFactory(
        builder: RSocketRequester.Builder,
        connection: ClientTransportFactory<ClientTransport>,
        clientProps: RSocketClientProperties,
    ): ClientFactory<RSocketRequester> = DefaultRequesterFactory(builder, connection, clientProps)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty("app.client.protocol", havingValue = "rsocket")
    fun requesterBuilder(strategies: RSocketStrategies): RSocketRequester.Builder =
        RSocketRequester.builder().rsocketStrategies(strategies)

    @Bean
    @ConditionalOnProperty("app.client.protocol", havingValue = "rsocket")
    fun <T> coreClientBeans(
        requesterFactory: ClientFactory<RSocketRequester>,
        clientProps: RSocketClientProperties,
        typeUtil: TypeUtil<T>
    ) = CoreRSocketClients<T, String, IndexSearchRequest>(
        requesterFactory,
        clientProps,
        typeUtil
    )

    @Bean
    @ConditionalOnProperty("app.client.protocol", havingValue = "rsocket")
    fun <T> compositeClientBeans(
        requesterFactory: ClientFactory<RSocketRequester>,
        clientProps: RSocketClientProperties
    ) = CompositeRSocketClients<T>(requesterFactory, clientProps)
}