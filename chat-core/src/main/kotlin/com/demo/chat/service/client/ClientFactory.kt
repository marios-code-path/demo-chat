package com.demo.chat.service.client

import reactor.core.publisher.Mono

interface ClientFactory<C> {
    fun getClient(serviceKey: String): C
    fun serviceDestination(serviceKey: String): String
}

interface ClientDiscovery<C> {
    fun serviceDestination(serviceKey: String): Mono<C>
}