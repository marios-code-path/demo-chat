package com.demo.chat.service.client

import org.springframework.cloud.client.ServiceInstance
import reactor.core.publisher.Mono

interface ClientFactory<C> {
    fun getClient(serviceKey: String): C
    fun serviceDestination(serviceKey: String): String
}

interface ClientDiscovery{
    fun serviceDestination(serviceKey: String): Mono<ServiceInstance>
}