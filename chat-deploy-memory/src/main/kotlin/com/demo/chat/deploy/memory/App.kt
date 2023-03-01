package com.demo.chat.deploy.memory

import com.demo.chat.config.controller.PersistenceControllersConfiguration
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.web.reactive.config.EnableWebFlux
import reactor.core.publisher.Hooks


@SpringBootApplication(scanBasePackages = ["com.demo.chat.config"])
@Profile("exec-chat")
class App {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Hooks.onOperatorDebug()
            runApplication<App>(*args)
        }
    }


    @ConditionalOnBean(PersistenceControllersConfiguration.UserPersistenceController::class)
    @Bean
    fun commandRunner(): ApplicationRunner = ApplicationRunner {
        println("Consul Discovery Persistence/Index/Messaging/Controllers App.")
    }
}