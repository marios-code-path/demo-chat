package com.demo.chat.client.rsocket

data class DiscoveryException(val servicePrefix: String) : RuntimeException("Cannot discover $servicePrefix Service")