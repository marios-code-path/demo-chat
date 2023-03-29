package com.demo.chat.client.rsocket

import com.demo.chat.config.client.rsocket.DiscoveryException
import com.demo.chat.service.client.ClientFactory
import com.demo.chat.service.client.ClientProperties
import com.demo.chat.service.client.ClientProperty
import com.demo.chat.service.client.transport.ClientTransportFactory
import io.rsocket.transport.ClientTransport
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.messaging.rsocket.RSocketRequester
import reactor.core.publisher.Mono
import java.util.*


class ConsulDiscoveryRequesterFactory(
    private val builder: RSocketRequester.Builder,
    private val discovery: ConsulReactiveDiscoveryClient,
    private val configProps: ClientProperties<ClientProperty>,
    private val connection: ClientTransportFactory<ClientTransport>
) : ClientFactory<RSocketRequester> {

    override fun getClient(serviceKey: String): RSocketRequester = discovery
        .getInstances(serviceDestination(serviceKey))
        .map { instance ->
            Optional.ofNullable(instance.metadata["rsocketPort"])
                .map {
                    builder
                        .transport(connection.tcpClientTransport(instance.host, it.toInt()))
                }
                .orElseThrow { DiscoveryException(serviceKey) }
        }
        .switchIfEmpty(Mono.error(DiscoveryException(serviceKey)))
        .blockFirst()!!

    override fun serviceDestination(serviceKey: String): String = configProps.getServiceConfig(serviceKey).dest!!
}