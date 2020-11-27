package com.demo.chat.deploy.app.memory

import com.demo.chat.deploy.config.JacksonConfiguration
import com.demo.chat.deploy.config.client.CoreServiceClientBeans
import com.demo.chat.deploy.config.client.CoreServiceClientFactory
import com.demo.chat.deploy.config.controllers.core.IndexControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.KeyControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.PersistenceControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.PubSubControllerConfiguration
import com.demo.chat.deploy.config.controllers.edge.*
import com.demo.chat.deploy.config.factory.ValueCodecFactory
import com.demo.chat.deploy.config.properties.AppConfigurationProperties
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import com.demo.chat.service.PubSubTopicExchangeService
import com.demo.chat.service.impl.lucene.index.IndexEntryEncoder
import com.demo.chat.service.impl.lucene.index.StringToKeyEncoder
import com.demo.chat.service.impl.memory.messaging.MemoryPubSubTopicExchange
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.util.*

@Configuration
class AppRSocketClientConfiguration(clients: CoreServiceClientFactory)
    : CoreServiceClientBeans<UUID, String, IndexSearchRequest>(clients)

@SpringBootApplication
@EnableConfigurationProperties(AppConfigurationProperties::class)
@Import(RSocketRequesterAutoConfiguration::class,
        JacksonConfiguration::class,
        CoreServiceClientFactory::class)
class App {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }

    @Configuration
    class IndexConfiguration {
        @Configuration
        @ConditionalOnProperty(prefix = "app.service.core", name = ["index"])
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
            @ConditionalOnProperty(prefix = "app.service.core", name = ["index"])
            class AppIndexControllers : IndexControllersConfiguration()
        }
    }

    @Configuration
    class PersistenceConfiguration {
        @Configuration
        @ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"])
        class PersistenceServices(keyService: IKeyService<UUID>) : InMemoryPersistenceConfiguration<UUID, String>(keyService)

        @Configuration
        @ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"])
        class PersistenceControllers : PersistenceControllersConfiguration()
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["key"])
    class KeyServiceConfiguration {
        @Bean
        fun keyService(): IKeyService<UUID> = KeyServiceInMemory { UUID.randomUUID() }

        @Configuration
        @ConditionalOnProperty(prefix = "app.service.core", name = ["key"])
        class KeyControllers : KeyControllersConfiguration()
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"])
    class PubSubConfiguration {
        @Bean
        fun memoryPubSub(): PubSubTopicExchangeService<UUID, String> = MemoryPubSubTopicExchange()

        @Configuration
        @ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"])
        class PubSubControllers : PubSubControllerConfiguration()
    }

    @Configuration
    class FunctionConfiguration {
        @Bean
        fun requestToIndexSearchRequest(): RequestToQueryConverter<UUID, IndexSearchRequest> = IndexSearchRequestConverters()

        @Bean
        fun stringCodecFactory(): ValueCodecFactory<String> =
                object : ValueCodecFactory<String> {
                    override fun emptyValue() = ""
                }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.edge", name = ["messaging"])
    class MessageConfiguration {
        @Configuration
        @ConditionalOnProperty(prefix = "app.service.edge", name = ["messaging"])
        class MessageControllers : MessageControllersConfiguration()
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.edge", name = ["user"])
    class UserConfiguration {
        @Configuration
        @ConditionalOnProperty(prefix = "app.service.edge", name = ["user"])
        class UserController : UserControllerConfiguration()
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.edge", name = ["topic"])
    class TopicConfiguration {

        @Configuration
        @ConditionalOnProperty(prefix = "app.service.edge", name = ["topic"])
        class TopicController : TopicControllerConfiguration()
    }
}