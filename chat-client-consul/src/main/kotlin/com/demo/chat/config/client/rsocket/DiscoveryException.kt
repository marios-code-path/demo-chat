package com.demo.chat.config.client.rsocket

data class DiscoveryException(val servicePrefix: String) : RuntimeException("Cannot discover $servicePrefix Service")