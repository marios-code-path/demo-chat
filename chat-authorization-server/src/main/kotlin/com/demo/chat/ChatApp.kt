package com.demo.chat

import com.demo.chat.security.service.CoreUserDetailsService
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
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

    @Bean
    fun runGet(bean: CoreUserDetailsService<Long>) =
        CommandLineRunner { args ->
        bean.findByUsername("Anon")
            .doOnNext {
                println("found ${it.username} found")
            }
            .subscribe()
    }
}