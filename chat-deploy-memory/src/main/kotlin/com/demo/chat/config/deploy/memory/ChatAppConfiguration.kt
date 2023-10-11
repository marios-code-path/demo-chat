package com.demo.chat.config.deploy.memory

import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener

@Configuration(proxyBeanMethods = false)
@Import(JacksonAutoConfiguration::class)
class ChatAppConfiguration {

    @EventListener
    fun readyListener(event: ApplicationReadyEvent) {
        println("In-Memory Deployment Started.")
    }
}