package com.demo.chat.deploy.app

import com.demo.chat.client.rsocket.config.CoreRSocketClients
import com.demo.chat.client.rsocket.config.RSocketCoreProperties
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
//        CoreClients::class,
//        EdgeClients::class,
//)
// TODO: This should also embody integration tests
class TestClient {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<TestClient>(*args)
        }
    }

    @Bean
    fun coreRSocketClientBeans(requesterFactory: RequesterFactory,
                               coreProps: RSocketCoreProperties) = CoreRSocketClients<UUID, String, IndexSearchRequest>(requesterFactory, coreProps, ParameterizedTypeReference.forType(UUID::class.java))

    @Configuration
    class ClientsBeansConfiguration(clients: CoreRSocketClients<UUID, String, IndexSearchRequest>) : AppClientBeansConfiguration<UUID, String, IndexSearchRequest>(
        clients,
        ParameterizedTypeReference.forType(UUID::class.java)
    )

    val logger = LoggerFactory.getLogger(this::class.java.canonicalName)

    @Bean
    @ConditionalOnProperty(prefix = "test", name = ["key"])
    fun keyRun(svc: IKeyService<UUID>): ApplicationRunner = ApplicationRunner {
        svc.key(UUID::class.java)
            .doOnNext {
                logger.info("KEY CREATED: ${it.id}")
            }
            .flatMap {
                svc.exists(it)
            }
            .map { exists ->
                logger.info("Key Exists: $exists")
            }
            .block()
    }
    
    @ConditionalOnProperty(prefix = "test", name = ["message"])
    @Bean
    fun messageRun(svc: MessagePersistenceClient<UUID, String>): ApplicationRunner = ApplicationRunner {
        svc.key()
            .flatMap {
                logger.info("NEW MESSAGE KEY: ${it.id}")
                svc.add(
                    Message.create(
                        MessageKey.create(it.id, UUID.randomUUID(), UUID.randomUUID()),
                        "HELLO", true
                    )
                )
            }
            .block()
    }

    @ConditionalOnProperty(prefix = "test", name = ["user"])
    @Bean
    fun userRun(svc: UserPersistenceClient<UUID>): ApplicationRunner = ApplicationRunner {
        svc.key()
            .flatMap {
                svc.add(User.create(it, "MARIO", "A_HANDLE", "http://localhost"))
            }
            .block()
    }
}