package com.demo.chat.deploy.app.memory

import com.demo.chat.config.CoreClientBeans
import com.demo.chat.deploy.config.client.AppClientBeansConfiguration
import com.demo.chat.deploy.config.client.consul.ConsulRequesterFactory
import com.demo.chat.controller.config.PersistenceControllersConfiguration
import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.deploy.config.properties.AppRSocketBindings
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import java.util.*

@SpringBootApplication
@EnableConfigurationProperties(AppRSocketBindings::class)
@Import(
    RSocketRequesterAutoConfiguration::class,
    DefaultChatJacksonModules::class,
    ConsulRequesterFactory::class,
    AppClientBeansConfiguration::class
)
class App { // Free The Types!

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }

    @Configuration
    class AppRSocketClientBeansConfiguration(clients: CoreClientBeans<UUID, String, IndexSearchRequest>) :
        AppClientBeansConfiguration<UUID, String, IndexSearchRequest>(
            clients,
            ParameterizedTypeReference.forType(UUID::class.java)
        )

    @Configuration
    class MemoryKeyServiceFactory : KeyServiceBeans<UUID> {
        override fun keyService() = KeyServiceInMemory { UUID.randomUUID() }
    }

    @ConditionalOnBean(PersistenceControllersConfiguration.UserPersistenceController::class)
    @Bean
    fun commandRunner(): ApplicationRunner = ApplicationRunner {
        println("Consul Discovery Persistence/Index/Messaging/Controllers App.")
    }
}