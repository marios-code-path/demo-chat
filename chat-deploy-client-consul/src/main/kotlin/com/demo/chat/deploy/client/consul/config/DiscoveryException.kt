package com.demo.chat.deploy.client.consul.config

data class DiscoveryException(val servicePrefix: String) : RuntimeException("Cannot discover $servicePrefix Service")