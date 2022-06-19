package com.demo.chat.deploy.app

import com.demo.chat.client.rsocket.config.CoreRSocketClients
import com.demo.chat.client.rsocket.config.CoreRSocketProperties
import com.demo.chat.client.rsocket.config.RequesterFactory
import com.demo.chat.client.rsocket.core.MessagePersistenceClient
import com.demo.chat.client.rsocket.core.UserPersistenceClient
import com.demo.chat.deploy.config.client.AppClientBeansConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.User
import com.demo.chat.service.IKeyService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.ParameterizedTypeReference
import java.util.*

//@EnableConfigurationProperties(AppConfigurationProperties::class)
//@SpringBootApplication
//@Import(
//        JacksonConfiguration::class,
//        RSocketStrategiesAutoConfiguration::class,
//        ConsulRequesterFactory::class,
//          AppClientBeansConfiguration::class,
//)
// TODO: This should also embody integration tests
class TestClient {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<TestClient>(*args)
        }
    }

    /**
     *     @Configuration
    class AppRSocketClientBeansConfiguration(clients: CoreClientBeans<Long, String, IndexSearchRequest>) :
    AppClientBeansConfiguration<Long, String, IndexSearchRequest>(
    clients,
    ParameterizedTypeReference.forType(Long::class.java)
    )

    @Bean
    fun coreRSocketClientBeans(requesterFactory: RequesterFactory,
    coreRSocketProps: CoreRSocketProperties
    ) = CoreRSocketClients<Long, String, IndexSearchRequest>(requesterFactory, coreRSocketProps, ParameterizedTypeReference.forType(Long::class.java))

     */
    @Bean
    fun coreRSocketClientBeans(requesterFactory: RequesterFactory,
                               coreRSocketProps: CoreRSocketProperties) = CoreRSocketClients<UUID, String, IndexSearchRequest>(requesterFactory, coreRSocketProps, ParameterizedTypeReference.forType(UUID::class.java))

    @Configuration
    class ClientsBeansConfiguration(clients: CoreRSocketClients<UUID, String, IndexSearchRequest>) : AppClientBeansConfiguration<UUID, String, IndexSearchRequest>(
        clients,
        ParameterizedTypeReference.forType(UUID::class.java)
    )

    val logger = LoggerFactory.getLogger(this::class.java.canonicalName)

}