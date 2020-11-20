package com.demo.chat.deploy.app.memory

import com.demo.chat.codec.Codec
import com.demo.chat.deploy.config.JacksonConfiguration
import com.demo.chat.deploy.config.client.CoreServiceClientBeans
import com.demo.chat.deploy.config.client.CoreServiceClientFactory
import com.demo.chat.deploy.config.client.RSocketClientProperties
import com.demo.chat.deploy.config.controllers.IndexControllersConfiguration
import com.demo.chat.deploy.config.controllers.KeyControllersConfiguration
import com.demo.chat.deploy.config.controllers.MessageControllersConfiguration
import com.demo.chat.deploy.config.controllers.PersistenceControllersConfiguration
import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import com.demo.chat.service.PubSubTopicExchangeService
import com.demo.chat.service.impl.memory.index.IndexEntryEncoder
import com.demo.chat.service.impl.memory.index.StringToKeyEncoder
import com.demo.chat.service.impl.memory.messaging.MemoryPubSubTopicExchange
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.util.*

@ConfigurationProperties("app.client.rsocket")
@ConstructorBinding
class AppRSocketClientProperties(
        override val key: String = "",
        override val index: String = "",
        override val persistence: String = "",
        override val messaging: String = "",
) : RSocketClientProperties

@Configuration
class AppRSocketClientConfiguration(clients: CoreServiceClientFactory) : CoreServiceClientBeans<UUID, String>(clients)

@SpringBootApplication
@EnableConfigurationProperties(AppRSocketClientProperties::class)
@Import(RSocketRequesterAutoConfiguration::class,
        JacksonConfiguration ::class,
        CoreServiceClientFactory::class)
class App {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service", name = ["index"])
    class IndexConfiguration {
        @Configuration
        class IndexServices : InMemoryIndexConfiguration<UUID, String>(
                StringToKeyEncoder { i -> Key.funKey(UUID.fromString(i)) },
                IndexEntryEncoder { t ->
                    listOf(
                            Pair("key", t.key.id.toString()),
                            Pair("handle", t.handle),
                            Pair("name", t.name)
                    )
                },
                IndexEntryEncoder { t ->
                    listOf(
                            Pair("key", t.key.id.toString()),
                            Pair("text", t.data)
                    )
                },
                IndexEntryEncoder { t ->
                    listOf(
                            Pair("key", t.key.id.toString()),
                            Pair("name", t.data)
                    )
                }) {

            @Configuration
            class AppIndexControllers : IndexControllersConfiguration()
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service", name = ["persistence"])
    class PersistenceConfiguration {
        @Configuration
        class PersistenceServices(keyService: IKeyService<UUID>) : InMemoryPersistenceConfiguration<UUID, String>(keyService)

        @Configuration
        class PersistenceControllers : PersistenceControllersConfiguration()
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service", name = ["key"])
    class KeyServiceConfiguration {
        @Bean
        fun keyService(): IKeyService<UUID> = KeyServiceInMemory(Codec { UUID.randomUUID() })

        @Configuration
        class KeyControllers : KeyControllersConfiguration()
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service", name = ["message"])
    class MessageConfiguration {
        @Bean
        fun messageExchange(): PubSubTopicExchangeService<UUID, String> = MemoryPubSubTopicExchange()

        @Configuration
        class MessageControllers : MessageControllersConfiguration()
    }
}