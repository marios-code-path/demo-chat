package com.demo.chat.deploy.config.client

data class DiscoveryException(val servicePrefix: String) : RuntimeException("Cannot discover $servicePrefix Service")