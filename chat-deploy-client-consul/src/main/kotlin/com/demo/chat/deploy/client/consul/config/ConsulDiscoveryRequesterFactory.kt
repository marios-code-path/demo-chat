package com.demo.chat.deploy.client.consul.config

import com.demo.chat.client.rsocket.config.RSocketClientProperties
import com.demo.chat.client.rsocket.config.RequesterFactory
import com.demo.chat.secure.rsocket.TransportFactory
import com.ecwid.consul.v1.ConsulClient
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.messaging.rsocket.RSocketRequester
import reactor.core.publisher.Mono
import java.util.*

class ConsulDiscoveryRequesterFactory(
    private val builder: RSocketRequester.Builder,
    client: ConsulClient,
    props: ConsulDiscoveryProperties,
    private val configProps: RSocketClientProperties,
    private val connection: TransportFactory
) : RequesterFactory {

    private val discovery: ReactiveDiscoveryClient = ConsulReactiveDiscoveryClient(client, props)

    override fun requester(serviceKey: String): RSocketRequester {
        return discovery
            .getInstances(serviceDestination(serviceKey))
            .map { instance ->
                Optional.ofNullable(instance.metadata["rsocket.port"])
                    .map {
                        builder
                            .connect(connection.tcpClientTransport(instance.host, it.toInt()))
                            .log()
                            .block()!!
                    }
                    .orElseThrow { DiscoveryException(serviceKey) }
            }
            .switchIfEmpty(Mono.error(DiscoveryException(serviceKey)))
            .blockFirst()!!
    }

    override fun serviceDestination(serviceKey: String): String = configProps.getServiceConfig(serviceKey).dest!!

}