package com.demo.chat.service.client

import org.springframework.cloud.client.ServiceInstance
import reactor.core.publisher.Mono

interface ClientFactory<C> {
    fun getClientForService(serviceName: String): C
}

interface ClientDiscovery{
    fun getServiceInstance(serviceName: String): Mono<ServiceInstance>
}

data class DiscoveryException(val servicePrefix: String) : RuntimeException("Cannot discover $servicePrefix Service")