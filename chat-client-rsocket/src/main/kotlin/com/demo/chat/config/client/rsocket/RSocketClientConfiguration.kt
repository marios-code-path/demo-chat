package com.demo.chat.config.client.rsocket

import com.demo.chat.client.rsocket.RSocketRequesterFactory
import com.demo.chat.client.rsocket.RequestMetadata
import com.demo.chat.client.rsocket.RequesterFactory
import com.demo.chat.client.rsocket.clients.CompositeRSocketClients
import com.demo.chat.client.rsocket.clients.CoreRSocketClients
import com.demo.chat.client.rsocket.transport.RSocketClientTransportFactory
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.client.ClientDiscovery
import com.demo.chat.service.client.ClientFactory
import org.slf4j.LoggerFactory
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
        @Autowired(required = false) requestMetadataProvider: Supplier<RequestMetadata>?,
    ): RequesterFactory = RSocketRequesterFactory(discovery, builder, connection, requestMetadataProvider ?: Supplier { Any() })

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