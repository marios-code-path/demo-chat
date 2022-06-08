package com.demo.chat.deploy.app.memory

import com.demo.chat.client.rsocket.config.CoreRSocketClients
import com.demo.chat.client.rsocket.config.CoreRSocketProperties
import com.demo.chat.client.rsocket.config.RequesterFactory
import com.demo.chat.client.rsocket.config.SecureConnection
import com.demo.chat.config.CoreClientBeans
import com.demo.chat.deploy.config.client.AppClientBeansConfiguration
import com.demo.chat.deploy.config.client.consul.ConsulRequesterFactory
import com.demo.chat.controller.config.PersistenceControllersConfiguration
import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.deploy.config.properties.AppRSocketBindings
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.secure.config.AuthConfiguration
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
import org.springframework.context.annotation.Profile
import org.springframework.core.ParameterizedTypeReference
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import java.util.*

@SpringBootApplication
@EnableConfigurationProperties(AppRSocketBindings::class)
@Import(
    RSocketRequesterAutoConfiguration::class,
    DefaultChatJacksonModules::class,
    ConsulRequesterFactory::class,
    SecureConnection::class,
    MemoryResourceConfiguration::class
)
class App {

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