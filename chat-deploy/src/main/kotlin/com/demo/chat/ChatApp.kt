package com.demo.chat

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.reactive.config.EnableWebFlux
import reactor.core.publisher.Hooks

@SpringBootApplication(proxyBeanMethods = false,
    scanBasePackages = ["com.demo.chat.config"],
)
@EnableWebFlux
class ChatApp {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Hooks.onOperatorDebug()
            runApplication<ChatApp>(*args)
        }
    }
}