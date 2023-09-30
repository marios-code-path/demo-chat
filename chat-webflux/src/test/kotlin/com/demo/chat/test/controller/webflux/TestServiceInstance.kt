package com.demo.chat.test.controller.webflux

import org.springframework.cloud.client.ServiceInstance
import java.net.URI

object TestServiceInstance : ServiceInstance {
    override fun getServiceId(): String = "ID"

    override fun getHost(): String = "localhost"

    override fun getPort(): Int = 80

    override fun isSecure(): Boolean = false

    override fun getUri(): URI = URI.create("ws://localhost/topic/listen")

    override fun getMetadata(): MutableMap<String, String> = mutableMapOf()

    override fun getScheme(): String = "ws"
}