package com.demo.chat.client.discovery

import com.demo.chat.service.client.ClientDiscovery
import com.demo.chat.service.client.ClientProperties
import com.demo.chat.service.client.ClientProperty
import com.demo.chat.service.client.DiscoveryException
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import reactor.core.publisher.Mono

class ConsulClientDiscovery(
    private val discovery: ConsulReactiveDiscoveryClient,
    private val configProps: ClientProperties<ClientProperty>,
) : ClientDiscovery {
    override fun getServiceInstance(serviceName: String): Mono<ServiceInstance> =
        discovery
            .getInstances(
                configProps.getServiceConfig(serviceName).dest
            )
            .next()
            .switchIfEmpty(Mono.error(DiscoveryException("$serviceName via ${configProps.getServiceConfig(serviceName).dest}")))
}