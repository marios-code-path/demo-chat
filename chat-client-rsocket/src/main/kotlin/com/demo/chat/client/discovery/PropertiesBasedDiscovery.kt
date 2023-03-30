package com.demo.chat.client.discovery

import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.service.client.ClientDiscovery
import org.springframework.cloud.client.DefaultServiceInstance
import org.springframework.cloud.client.ServiceInstance
import reactor.core.publisher.Mono

class PropertiesBasedDiscovery(
    private val clientProps: RSocketClientProperties
) : ClientDiscovery {
    override fun getServiceInstance(serviceName: String): Mono<ServiceInstance> =
        Mono.create { sink ->
            sink.success(
                clientProps.getServiceConfig(serviceName).dest!!
                    .split(":")
                    .zipWithNext()
                    .map { DefaultServiceInstance(serviceName, serviceName, it.first, it.second.toInt(), true) }
                    .single()
            )
        }
}