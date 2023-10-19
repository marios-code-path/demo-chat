package com.demo.chat

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import reactor.core.publisher.Hooks

@SpringBootApplication(
    scanBasePackages = ["com.demo.chat.config"],
    proxyBeanMethods = false
)
class ChatApp {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Hooks.onOperatorDebug()
            runApplication<ChatApp>(*args)
        }
    }
}