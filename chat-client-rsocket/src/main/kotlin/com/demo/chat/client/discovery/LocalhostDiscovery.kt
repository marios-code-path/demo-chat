package com.demo.chat.client.discovery

import com.demo.chat.service.client.ClientDiscovery
import org.springframework.cloud.client.DefaultServiceInstance
import org.springframework.cloud.client.ServiceInstance
import reactor.core.publisher.Mono

class LocalhostDiscovery : ClientDiscovery {
    override fun getServiceInstance(serviceName: String): Mono<ServiceInstance> =
        Mono.create { sink ->
            sink.success(
                DefaultServiceInstance(serviceName, serviceName, "127.0.0.1", 6790, true)
            )
        }
}