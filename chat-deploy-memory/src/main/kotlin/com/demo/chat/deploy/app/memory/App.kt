package com.demo.chat.deploy.app.memory

import com.demo.chat.deploy.config.client.CoreClientConfiguration
import com.demo.chat.deploy.config.client.CoreClients
import com.demo.chat.deploy.config.client.EdgeClients
import com.demo.chat.deploy.config.client.consul.ConsulRequesterFactory
import com.demo.chat.deploy.config.controllers.core.PersistenceControllersConfiguration
import com.demo.chat.deploy.config.core.KeyServiceConfiguration
import com.demo.chat.deploy.config.properties.AppConfigurationProperties
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
import java.util.*

@SpringBootApplication
@EnableConfigurationProperties(AppConfigurationProperties::class)
@Import(
        RSocketRequesterAutoConfiguration::class,
        DefaultChatJacksonModules::class,
        ConsulRequesterFactory::class,
        CoreClients::class,
        EdgeClients::class,
)
class App {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }

    @Configuration
    class AppRSocketClientConfiguration(clients: CoreClients)
        : CoreClientConfiguration<UUID, String, IndexSearchRequest>(clients)

    @Configuration
    class MemoryKeyServiceFactory : KeyServiceConfiguration<UUID> {
        override fun keyService() = KeyServiceInMemory { UUID.randomUUID() }
    }

    @ConditionalOnBean(PersistenceControllersConfiguration.UserPersistenceController::class)
    @Bean
    fun commandRunner(): ApplicationRunner = ApplicationRunner {
        println("THE Persistence/Controllers was present")
    }
}