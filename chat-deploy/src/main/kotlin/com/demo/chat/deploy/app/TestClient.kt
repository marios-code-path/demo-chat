package com.demo.chat.deploy.app

import com.demo.chat.ByHandleRequest
import com.demo.chat.UserCreateRequest
import com.demo.chat.client.rsocket.core.MessagePersistenceClient
import com.demo.chat.client.rsocket.core.UserPersistenceClient
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.User
import com.demo.chat.service.IKeyService
import com.demo.chat.deploy.config.JacksonConfiguration
import com.demo.chat.deploy.config.client.CoreServiceClientBeans
import com.demo.chat.deploy.config.client.CoreServiceClientFactory
import com.demo.chat.deploy.config.client.RSocketClientProperties
import com.demo.chat.domain.IndexSearchRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.util.*

@ConfigurationProperties("app.client.rsocket.destination")
@ConstructorBinding
class TestRSocketClientProperties(
        override val key: String = "",
        override val index: String = "",
        override val persistence: String = "",
        override val messaging: String = "",
        override val pubsub: String = ""
) : RSocketClientProperties

@EnableConfigurationProperties(TestRSocketClientProperties::class)
@SpringBootApplication
@Import(
        JacksonConfiguration::class,
        RSocketStrategiesAutoConfiguration::class,
        CoreServiceClientFactory::class
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
    class ClientsConfiguration(f: CoreServiceClientFactory) : CoreServiceClientBeans<UUID, String, IndexSearchRequest>(f)

    val logger = LoggerFactory.getLogger(this::class.java.canonicalName)

    @Bean
    @ConditionalOnProperty(prefix = "app.client.rsocket", name = ["key"])
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

    @Bean
    @ConditionalOnProperty(prefix = "app.client.rsocket", name = ["edge"])
    fun edgeRun(builder: CoreServiceClientFactory): ApplicationRunner = ApplicationRunner { appArgs ->
        builder
                .requester("core-service-rsocket")
                .route("edge.user.user-add")
                .data(UserCreateRequest("MG", "1002", "JPG"))
                .retrieveMono(UUID::class.java)
                .doOnNext {
                    logger.info("UUID FOUND: $it")
                }
                .flatMap {
                    builder.requester("core-service-rsocket")
                            .route("edge.user.user-by-handle")
                            .data(ByHandleRequest("1001"))
                            .retrieveMono(User::class.java)
                }
                .doOnNext {
                    logger.info("User Found ${it.key.id}: ${it.handle} / ${it.name}")
                }
                .block()
    }


    @ConditionalOnProperty(prefix = "app.client.rsocket.persistence", name = ["message"])
    @Bean
    fun messageRun(svc: MessagePersistenceClient<UUID, String>): ApplicationRunner = ApplicationRunner {
        svc.key()
                .flatMap {
                    logger.info("NEW MESSAGE KEY: ${it.id}")
                    svc.add(Message.create(
                            MessageKey.create(it.id, UUID.randomUUID(), UUID.randomUUID()),
                            "HELLO", true
                    ))
                }
                .block()
    }


    @ConditionalOnProperty(prefix = "app.client.rsocket.persistence", name = ["user"])
    @Bean
    fun userRun(svc: UserPersistenceClient<UUID>): ApplicationRunner {
        val applicationRunner = ApplicationRunner {
            svc.key()
                    .flatMap {
                        logger.info("NEW USER KEY: ${it.id}")
                        svc.add(User.create(it, "MARIO", "A_HANDLE", "http://localhost"))
                    }
                    .block()
        }
        return applicationRunner
    }
}