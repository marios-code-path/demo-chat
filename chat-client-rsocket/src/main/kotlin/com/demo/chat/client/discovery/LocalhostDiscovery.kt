package com.demo.chat.client.discovery

import com.demo.chat.service.client.ClientDiscovery
import org.springframework.cloud.client.DefaultServiceInstance
import org.springframework.cloud.client.ServiceInstance
import reactor.core.publisher.Mono

class LocalhostDiscovery(val host: String, val port: Int) : ClientDiscovery {
    override fun getServiceInstance(serviceName: String): Mono<ServiceInstance> =
        Mono.create { sink ->
            sink.success(
                DefaultServiceInstance(serviceName, serviceName, host, port, true)
            )
        }
}