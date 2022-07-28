package com.demo.chat.deploy.memory

import com.demo.chat.controller.config.PersistenceControllersConfiguration
import com.demo.chat.deploy.memory.config.CoreControllerConfiguration
import com.demo.chat.deploy.memory.config.EdgeControllerConfiguration
import com.demo.chat.deploy.memory.config.MemoryResourceConfiguration
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.secure.config.AuthConfiguration
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity

@EnableDiscoveryClient
@SpringBootApplication
@Import(
    JacksonAutoConfiguration::class,
    RSocketStrategiesAutoConfiguration::class,
    RSocketMessagingAutoConfiguration::class,
    DefaultChatJacksonModules::class,
    MemoryResourceConfiguration::class,
    CoreControllerConfiguration::class,
    EdgeControllerConfiguration::class
)
class  App {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }

    @Configuration
    @EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
    class SecurityConfiguration : AuthConfiguration<Long>(TypeUtil.LongUtil, Key.funKey(0L))

    @ConditionalOnBean(PersistenceControllersConfiguration.UserPersistenceController::class)
    @Bean
    fun commandRunner(): ApplicationRunner = ApplicationRunner {
        println("Consul Discovery Persistence/Index/Messaging/Controllers App.")
    }
}