package com.demo.chat.deploy.memory

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import reactor.core.publisher.Hooks

@SpringBootApplication(scanBasePackages = ["com.demo.chat.config"],
    proxyBeanMethods = false)
@Profile("memory")
class MemoryDeploymentApp {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Hooks.onOperatorDebug()
            runApplication<MemoryDeploymentApp>(*args)
        }
    }

    @EventListener
    fun readyListener(event: ApplicationReadyEvent) {
        println("In-Memory Deployment Started.")
    }
}