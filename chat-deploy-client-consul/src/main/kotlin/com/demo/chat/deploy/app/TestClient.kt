package com.demo.chat.deploy.app

import com.demo.chat.client.rsocket.config.*
import com.demo.chat.client.rsocket.core.impl.UserPersistenceClient
import com.demo.chat.config.CoreClientBeans
import com.demo.chat.deploy.config.client.AppClientBeansConfiguration
import com.demo.chat.deploy.config.client.consul.ConsulRequesterFactory
import com.demo.chat.deploy.config.properties.AppRSocketProperties
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.UserPersistence
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import java.util.*

@EnableConfigurationProperties(AppRSocketProperties::class)
@SpringBootApplication
@Import(
    DefaultChatJacksonModules::class,
    JacksonAutoConfiguration::class,
    SecureConnection::class,
    RSocketStrategiesAutoConfiguration::class,
    DefaultRequesterFactory::class,
    //ConsulRequesterFactory::class,
)
// TODO: This should also embody integration tests
class TestClient {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<TestClient>(*args)
        }
    }

    @Configuration
    class AppRSocketClientBeansConfiguration(clients: CoreClientBeans<Long, String, IndexSearchRequest>) :
        AppClientBeansConfiguration<Long, String, IndexSearchRequest>(
            clients,
            ParameterizedTypeReference.forType(Long::class.java)
        )

    @Bean
    fun coreRSocketClientBeans(
        requesterFactory: RequesterFactory,
        appRSocketProps: AppRSocketProperties
    ) = CoreRSocketClients<Long, String, IndexSearchRequest>(
        requesterFactory,
        appRSocketProps.core,
        ParameterizedTypeReference.forType(Long::class.java)
    )

//    @Bean
//    fun coreRSocketClientBeans(requesterFactory: RequesterFactory,
//                               coreRSocketProps: CoreRSocketProperties) = CoreRSocketClients<UUID, String, IndexSearchRequest>(requesterFactory, coreRSocketProps, ParameterizedTypeReference.forType(UUID::class.java))
//
//    @Configuration
//    class ClientsBeansConfiguration(clients: CoreRSocketClients<UUID, String, IndexSearchRequest>) : AppClientBeansConfiguration<UUID, String, IndexSearchRequest>(
//        clients,
//        ParameterizedTypeReference.forType(UUID::class.java)
//    )

    val logger = LoggerFactory.getLogger(this::class.java.canonicalName)

}