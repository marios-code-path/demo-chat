package com.demo.chat

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.security.autoconfigure.rsocket.RSocketSecurityAutoConfiguration
import org.springframework.boot.runApplication
import reactor.core.publisher.Hooks

@SpringBootApplication(proxyBeanMethods = false,
    scanBasePackages = ["com.demo.chat.config"],
    exclude = [RSocketSecurityAutoConfiguration::class],
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
