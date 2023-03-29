package com.demo.chat.config.client.rsocket

import com.demo.chat.client.rsocket.ConsulDiscoveryRequesterFactory
import com.demo.chat.service.client.ClientProperties
import com.demo.chat.service.client.ClientProperty
import com.demo.chat.service.client.transport.ClientTransportFactory
import io.rsocket.transport.ClientTransport
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.rsocket.RSocketRequester

@Configuration
@ConditionalOnProperty(
    "app.rsocket.client.requester.factory",
    havingValue = "consul"
)
class ConsulRequesterFactoryConfiguration {

    @Bean
    fun consulRequesterFactory(
        builder: RSocketRequester.Builder,
        discovery: ConsulReactiveDiscoveryClient,
        configProps: ClientProperties<ClientProperty>,
        connection: ClientTransportFactory<ClientTransport>
    ) = ConsulDiscoveryRequesterFactory(builder, discovery, configProps, connection)
}