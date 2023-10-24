package com.demo.chat.config.client.rsocket

import com.demo.chat.client.rsocket.RSocketRequesterFactory
import com.demo.chat.client.rsocket.RequestMetadata
import com.demo.chat.client.rsocket.RequesterFactory
import com.demo.chat.client.rsocket.clients.CompositeRSocketClients
import com.demo.chat.client.rsocket.clients.CoreRSocketClients
import com.demo.chat.client.rsocket.transport.RSocketClientTransportFactory
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.CoreServices
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.client.ClientDiscovery
import com.demo.chat.service.client.ClientFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import java.util.function.Supplier

@Configuration
@ConditionalOnProperty("app.client.protocol", havingValue = "rsocket")
class RSocketClientConfiguration {

    @Bean
    fun requesterFactory(
        builder: RSocketRequester.Builder,
        connection: RSocketClientTransportFactory,
        discovery: ClientDiscovery,
        @Autowired(required = false) simpleRequestMetadataProvider: Supplier<RequestMetadata>?,
    ): RequesterFactory = RSocketRequesterFactory(discovery, builder, connection, simpleRequestMetadataProvider ?: Supplier { Any() })

    @Bean
    @ConditionalOnMissingBean
    fun requesterBuilder(strategies: RSocketStrategies): RSocketRequester.Builder =
        RSocketRequester.builder().rsocketStrategies(strategies)

    @Bean
    fun <T> coreClientBeans(
        requesterFactory: ClientFactory<RSocketRequester>,
        clientProps: RSocketClientProperties,
        typeUtil: TypeUtil<T>
    ): CoreServices<T, String, IndexSearchRequest> = CoreRSocketClients<T, String, IndexSearchRequest>(
        requesterFactory,
        clientProps,
        typeUtil
    )

    @Bean
    fun <T> compositeClientBeans(
        requesterFactory: ClientFactory<RSocketRequester>,
        clientProps: RSocketClientProperties,
        typeUtil: TypeUtil<T>
    ): CompositeServiceBeans<T, String> = CompositeRSocketClients(requesterFactory, clientProps)
}