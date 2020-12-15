package com.demo.chat.deploy.config.client.consul

import com.demo.chat.deploy.config.client.AppDiscoveryException
import com.demo.chat.deploy.config.client.RequesterFactory
import com.demo.chat.deploy.config.properties.AppConfigurationProperties
import com.demo.chat.deploy.config.properties.RSocketCoreProperties
import com.demo.chat.deploy.config.properties.RSocketEdgeProperties
import com.ecwid.consul.v1.ConsulClient
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.messaging.rsocket.RSocketRequester
import reactor.core.publisher.Mono
import java.util.*

class ConsulRequesterFactory(private val builder: RSocketRequester.Builder,
                             client: ConsulClient,
                             props: ConsulDiscoveryProperties,
                             configProps: AppConfigurationProperties,
) : RequesterFactory {
    private val coreProps: RSocketCoreProperties = configProps.core
    private val edgeProps: RSocketEdgeProperties = configProps.edge

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
        "key" -> coreProps.key.dest
        "index" -> coreProps.index.dest
        "persistence" -> coreProps.persistence.dest
        "pubsub" -> coreProps.pubsub.dest
        "topic" -> edgeProps.topic.dest
        "user" -> edgeProps.user.dest
        "message" -> edgeProps.message.dest
        else -> throw AppDiscoveryException(serviceKey)
    }

}