package com.demo.chat.deploy.app.memory

import com.demo.chat.ByHandleRequest
import com.demo.chat.ByIdRequest
import com.demo.chat.ByNameRequest
import com.demo.chat.client.rsocket.core.PubSubClient
import com.demo.chat.codec.Codec
import com.demo.chat.deploy.config.JacksonConfiguration
import com.demo.chat.deploy.config.client.CoreServiceClientBeans
import com.demo.chat.deploy.config.client.CoreServiceClientFactory
import com.demo.chat.deploy.config.client.RSocketClientProperties
import com.demo.chat.deploy.config.controllers.core.IndexControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.KeyControllersConfiguration
import com.demo.chat.deploy.config.controllers.edge.MessageControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.PersistenceControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.PubSubControllerConfiguration
import com.demo.chat.deploy.config.controllers.edge.TopicControllerConfiguration
import com.demo.chat.deploy.config.controllers.edge.UserControllerConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.service.*
import com.demo.chat.service.impl.lucene.index.IndexEntryEncoder
import com.demo.chat.service.impl.lucene.index.StringToKeyEncoder
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
import org.springframework.core.ParameterizedTypeReference
import java.util.*
import java.util.function.Function
import java.util.function.Supplier

@ConfigurationProperties("app.client.rsocket")
@ConstructorBinding
class AppRSocketClientProperties(
        override val key: String = "",
        override val index: String = "",
        override val persistence: String = "",
        override val messaging: String = "",
        override val pubsub: String = ""
) : RSocketClientProperties

@Configuration
class AppRSocketClientConfiguration(clients: CoreServiceClientFactory)
    : CoreServiceClientBeans<UUID, String, IndexSearchRequest>(clients)

@SpringBootApplication
@EnableConfigurationProperties(AppRSocketClientProperties::class)
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
    @ConditionalOnProperty(prefix = "app.service", name= ["pubsub"])
    class PubSubConfiguration {
        @Bean
        fun memoryPubSub(): PubSubTopicExchangeService<UUID, String> = MemoryPubSubTopicExchange()

        @Configuration
        class PubSubControllers : PubSubControllerConfiguration()
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.edge", name = ["messaging"])
    class MessageConfiguration {
        @Bean
        fun queryConvert() = Function<ByIdRequest<UUID>, IndexSearchRequest> { i -> IndexSearchRequest(TopicIndexService.ID, i.id.toString(), 100) }

        @Configuration
        class MessageControllers : MessageControllersConfiguration()
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.edge", name = ["user"])
    class UserConfiguration {
        @Bean
        fun queryConvert():Function<ByHandleRequest, IndexSearchRequest> =
                Function { i -> IndexSearchRequest(UserIndexService.HANDLE, i.handle, 100) }

        @Configuration
        class UserController : UserControllerConfiguration()
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.edge", name = ["topic"])
    class TopicConfiguration {
        @Bean
        fun emptyDataSupplier() = Supplier<String> { "" }

        @Bean
        fun topicNameToQuery() =
                Function<ByNameRequest, IndexSearchRequest>
                { i -> IndexSearchRequest(TopicIndexService.NAME, i.name, 100) }

        @Bean
        fun membershipIdToQuery() =
                Function<ByIdRequest<UUID>, IndexSearchRequest>
                { i -> IndexSearchRequest(MembershipIndexService.MEMBEROF, i.id.toString(), 100) }

        @Configuration
        class TopicController : TopicControllerConfiguration()
    }

}