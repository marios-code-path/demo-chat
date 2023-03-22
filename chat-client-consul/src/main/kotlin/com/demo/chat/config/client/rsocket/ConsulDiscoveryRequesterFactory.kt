package com.demo.chat.config.client.rsocket

import com.demo.chat.client.rsocket.RequesterFactory
import com.demo.chat.secure.transport.TransportFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.rsocket.RSocketRequester
import reactor.core.publisher.Mono
import java.util.*


@Configuration
@ConditionalOnProperty(
    "app.rsocket.client.requester.factory",
    havingValue = "consul"
)
class ConsulDiscoveryRequesterFactory(
    private val builder: RSocketRequester.Builder,
    private val discovery: ConsulReactiveDiscoveryClient,
    private val configProps: RSocketClientProperties,
    private val connection: TransportFactory
) : RequesterFactory {

    override fun requester(serviceKey: String): RSocketRequester {

        return discovery
            .getInstances(serviceDestination(serviceKey))
            .map { instance ->
                Optional.ofNullable(instance.metadata["rsocket.port"])
                    .map {
                        builder
                            .transport(connection.tcpClientTransport(instance.host, it.toInt()))
                    }
                    .orElseThrow { DiscoveryException(serviceKey) }
            }
            .switchIfEmpty(Mono.error(DiscoveryException(serviceKey)))
            .blockFirst()!!
    }

    override fun serviceDestination(serviceKey: String): String = configProps.getServiceConfig(serviceKey).dest!!
}