package com.demo.chat.deploy.config.client.consul

import com.demo.chat.client.rsocket.config.AppRSocketProperties
import com.demo.chat.client.rsocket.config.CoreRSocketProperties
import com.demo.chat.deploy.config.client.AppDiscoveryException
import com.demo.chat.client.rsocket.config.RequesterFactory
import com.demo.chat.client.rsocket.config.SecureConnection
import com.ecwid.consul.v1.ConsulClient
import io.rsocket.transport.netty.client.TcpClientTransport
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.core.SpringProperties
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import reactor.core.publisher.Mono
import java.util.*

class ConsulDiscoveryRequesterFactory(
    private val builder: RSocketRequester.Builder,
    client: ConsulClient,
    props: ConsulDiscoveryProperties,
    configProps: AppRSocketProperties,
    private val connection: SecureConnection
) : RequesterFactory {
    private val coreRSocketProps: CoreRSocketProperties = configProps.core

    val discovery: ReactiveDiscoveryClient = ConsulReactiveDiscoveryClient(client, props)

    override fun requester(serviceKey: String): RSocketRequester {
        return discovery
            .getInstances(serviceDestination(serviceKey))
            .map { instance ->
                Optional.ofNullable(instance.metadata["rsocket.port"])
                    .map {
                        builder
                            .connect(connection.tcpClientTransport(instance.host, it.toInt(), false))
                            .log()
                            .block()!!
                    }
                    .orElseThrow { AppDiscoveryException(serviceKey) }
            }
            .switchIfEmpty(Mono.error(AppDiscoveryException(serviceKey)))
            .blockFirst()!!
    }

    override fun serviceDestination(serviceKey: String): String = when (serviceKey) {
        "key" -> coreRSocketProps.key.dest
        "index" -> coreRSocketProps.index.dest
        "persistence" -> coreRSocketProps.persistence.dest
        "pubsub" -> coreRSocketProps.pubsub.dest
        "command" -> SpringProperties.getProperty("route.host.destination")
        else -> throw AppDiscoveryException(serviceKey)
    }!!
}