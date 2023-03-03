package com.demo.chat.deploy.memory

import com.demo.chat.config.controller.core.PersistenceControllersConfiguration
import com.demo.chat.domain.TypeUtil
import com.demo.chat.rsocket.TargetIdentifierInterceptor
import io.rsocket.core.RSocketServer
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.rsocket.server.RSocketServerCustomizer
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.messaging.rsocket.RSocketStrategies
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

    @Configuration
    class ServerCustomize<T>(private val strategies: RSocketStrategies, private val typeUtil: TypeUtil<T>) : RSocketServerCustomizer {
        override fun customize(rSocketServer: RSocketServer) {
            rSocketServer.interceptors { it.forResponder(TargetIdentifierInterceptor(strategies, typeUtil)) }
        }
    }

    @ConditionalOnBean(PersistenceControllersConfiguration.UserPersistenceController::class)
    @Bean
    fun commandRunner(): ApplicationRunner = ApplicationRunner {
        println("Consul Discovery Persistence/Index/Messaging/Controllers App.")
    }
}