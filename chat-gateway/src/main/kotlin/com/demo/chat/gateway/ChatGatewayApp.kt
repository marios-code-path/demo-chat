package com.demo.chat.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.reactive.config.EnableWebFlux

@SpringBootApplication
@EnableWebFlux
class ChatGatewayApp {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<ChatGatewayApp>(*args)
        }
    }
}


