package com.demo.chat.client.rsocket

import com.demo.chat.config.client.rsocket.DiscoveryException
import com.demo.chat.service.client.ClientDiscovery
import com.demo.chat.service.client.ClientFactory
import com.demo.chat.service.client.ClientProperties
import com.demo.chat.service.client.ClientProperty
import com.demo.chat.service.client.transport.ClientTransportFactory
import io.rsocket.transport.ClientTransport
import org.springframework.cloud.client.ServiceInstance
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

    override fun getClient(serviceKey: String): RSocketRequester {
        println("Getting client for $serviceKey")
        println("dest: " + serviceDestination(serviceKey))
        discovery
            .getInstances(serviceDestination(serviceKey))
            .doOnNext { println("Found: ${it.instanceId}, ${it.serviceId}, ${it.host} / ${it.uri} | ${it.port}") }
            .blockLast()

        return discovery
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
    }

    override fun serviceDestination(serviceKey: String): String = configProps.getServiceConfig(serviceKey).dest!!
}