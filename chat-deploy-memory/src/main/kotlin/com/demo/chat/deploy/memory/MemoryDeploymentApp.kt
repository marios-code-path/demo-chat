package com.demo.chat.deploy.memory

import com.demo.chat.config.controller.core.PersistenceControllersConfiguration
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import reactor.core.publisher.Hooks


@SpringBootApplication(scanBasePackages = ["com.demo.chat.config"], proxyBeanMethods = false)
@Profile("exec-chat")
@Import(
    RSocketServerConfiguration::class,
    WebSecurityConfiguration::class
)
class MemoryDeploymentApp {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Hooks.onOperatorDebug()
            runApplication<MemoryDeploymentApp>(*args)
        }
    }

    @ConditionalOnBean(PersistenceControllersConfiguration.UserPersistenceController::class)
    @Bean
    fun commandRunner(): ApplicationRunner = ApplicationRunner {
        println("In-Memory Deployment Started.")
    }
}

