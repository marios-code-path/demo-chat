package com.demo.chat.config.gateway

import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.EnableWebFlux

@Configuration(proxyBeanMethods = false)
@EnableWebFlux
class ChatGatewayAppConfiguration {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<ChatGatewayAppConfiguration>(*args)
        }
    }
}


