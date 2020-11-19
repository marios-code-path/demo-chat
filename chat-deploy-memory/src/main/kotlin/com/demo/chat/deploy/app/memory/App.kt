package com.demo.chat.deploy.app.memory

import com.demo.chat.codec.EmptyUUIDCodec
import com.demo.chat.controller.edge.MessagingController
import com.demo.chat.controller.service.IndexServiceController
import com.demo.chat.controller.service.KeyServiceController
import com.demo.chat.controller.service.PersistenceServiceController
import com.demo.chat.deploy.config.client.RSocketClientConfiguration
import com.demo.chat.deploy.config.client.RSocketClientFactory
import com.demo.chat.deploy.config.SerializationConfiguration
import com.demo.chat.deploy.config.client.RSocketClientProperties
import com.demo.chat.deploy.config.core.IndexControllersConfiguration
import com.demo.chat.deploy.config.core.KeyControllersConfiguration
import com.demo.chat.deploy.config.core.MessageControllersConfiguration
import com.demo.chat.deploy.config.core.PersistenceControllersConfiguration
import com.demo.chat.domain.*
import com.demo.chat.service.*
import com.demo.chat.service.impl.memory.index.IndexEntryEncoder
import com.demo.chat.service.impl.memory.index.QueryCommand
import com.demo.chat.service.impl.memory.index.StringToKeyEncoder
import com.demo.chat.service.impl.memory.messaging.MemoryPubSubTopicExchange
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import com.demo.chat.service.impl.memory.persistence.MembershipPersistenceInMemory
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
import org.springframework.context.annotation.Profile
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.*

object UUIDCodec : EmptyUUIDCodec()

@ConfigurationProperties("app.client.rsocket")
@ConstructorBinding
class AppRSocketClientProperties(
        override val key: String = "",
        override val index: String = "",
        override val persistence: String = "",
        override val messaging: String = "",
) : RSocketClientProperties

@Configuration
class AppRSocketClientConfiguration(clients: RSocketClientFactory) : RSocketClientConfiguration<UUID, String>(clients)

@SpringBootApplication
@EnableConfigurationProperties(AppRSocketClientProperties::class)
@Import(RSocketRequesterAutoConfiguration::class,
        SerializationConfiguration::class,
        RSocketClientFactory::class)
class App {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service", name = ["index"])
    class IndexConfiguration : InMemoryIndexConfiguration<UUID, String>(
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
        class AppIndexControllers: IndexControllersConfiguration()
    }

    @Configuration
    class PersistenceConfiguration(keyService: IKeyService<UUID>) : InMemoryPersistenceConfiguration<UUID, String>(keyService) {
        @Configuration
        class AppPersistenceConfiguration() : PersistenceControllersConfiguration()
    }

    @Configuration
    class KeyConfiguration : KeyControllersConfiguration() {
        @Bean
        @ConditionalOnProperty(prefix = "app.service", name = ["key"])
        fun keyService(): IKeyService<UUID> = KeyServiceInMemory(UUIDCodec)

    }

    @Configuration
    class MessageConfiguration : MessageControllersConfiguration() {
        @Bean
        @ConditionalOnProperty(prefix = "app.service", name = ["message"])
        fun messageExchange(): PubSubTopicExchangeService<UUID, String> = MemoryPubSubTopicExchange()
    }

}

