package com.demo.chat.deploy.config.client.consul

import com.demo.chat.client.rsocket.config.CoreRSocketProperties
import com.demo.chat.deploy.config.client.AppDiscoveryException
import com.demo.chat.client.rsocket.config.RequesterFactory
import com.demo.chat.deploy.config.properties.AppRSocketProperties
import com.ecwid.consul.v1.ConsulClient
import io.rsocket.transport.netty.client.TcpClientTransport
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.core.SpringProperties
import org.springframework.messaging.rsocket.RSocketRequester
import reactor.core.publisher.Mono
import java.util.*

class ConsulRequesterFactory(private val builder: RSocketRequester.Builder,
                             client: ConsulClient,
                             props: ConsulDiscoveryProperties,
                             configProps: AppRSocketProperties,
                             transport: TcpClientTransport
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
                                        .connectTcp(instance.host, it.toInt())
                                        .log()
                                        .block()!!
                            }
                            .orElseThrow { AppDiscoveryException(serviceKey) }
                }
                .switchIfEmpty(Mono.error(AppDiscoveryException(serviceKey)))
                .blockFirst()!!
    }

    /**
     * TODO: Index these properties to eliminate static decision tree.
     */
    private fun serviceDestination(serviceKey: String) = when (serviceKey) {
        "key" -> coreRSocketProps.key.dest
        "index" -> coreRSocketProps.index.dest
        "persistence" -> coreRSocketProps.persistence.dest
        "pubsub" -> coreRSocketProps.pubsub.dest
        "command" -> SpringProperties.getProperty("route.host.destination")
        else -> throw AppDiscoveryException(serviceKey)
    }

}