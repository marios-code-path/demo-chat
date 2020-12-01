package com.demo.chat.deploy.app.memory

import com.demo.chat.controller.edge.MessagingController
import com.demo.chat.controller.edge.TopicServiceController
import com.demo.chat.controller.edge.UserServiceController
import com.demo.chat.deploy.config.JacksonConfiguration
import com.demo.chat.deploy.config.client.CoreServiceClientBeans
import com.demo.chat.deploy.config.client.CoreServiceClientFactory
import com.demo.chat.deploy.config.client.EdgeServiceClientFactory
import com.demo.chat.deploy.config.client.consul.ConsulRequesterFactory
import com.demo.chat.deploy.config.controllers.core.IndexControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.KeyControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.PersistenceControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.PubSubControllerConfiguration
import com.demo.chat.deploy.config.controllers.edge.*
import com.demo.chat.deploy.config.core.IndexServiceFactory
import com.demo.chat.deploy.config.core.KeyServiceFactory
import com.demo.chat.deploy.config.core.PersistenceServiceFactory
import com.demo.chat.deploy.config.core.ValueCodecFactory
import com.demo.chat.deploy.config.properties.AppConfigurationProperties
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
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.*

@SpringBootApplication
@EnableConfigurationProperties(AppConfigurationProperties::class)
@Import(RSocketRequesterAutoConfiguration::class,
        JacksonConfiguration::class,
        ConsulRequesterFactory::class,
        CoreServiceClientFactory::class,
        EdgeServiceClientFactory::class,
)
class App {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }

    @Configuration
    class BaseAppState {
        @Configuration
        class AppRSocketClientConfiguration(clients: CoreServiceClientFactory)
            : CoreServiceClientBeans<UUID, String, IndexSearchRequest>(clients)

        @Configuration
        class MemoryKeyServiceFactory : KeyServiceFactory<UUID> {
            override fun keyService() = KeyServiceInMemory { UUID.randomUUID() }
        }

        @Configuration
        class PersistenceServicesFactory(keyFactory: KeyServiceFactory<UUID>)
            : InMemoryPersistenceFactory<UUID, String>(keyFactory.keyService())

        @Configuration
        class IndexServicesFactory : InMemoryIndexFactory<UUID, String>(
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
                },
                IndexEntryEncoder { t ->
                    listOf(
                            Pair("key", Key.funKey(t.key).toString()),
                            Pair(MembershipIndexService.MEMBER, t.member.toString()),
                            Pair(MembershipIndexService.MEMBEROF, t.memberOf.toString())
                    )
                })
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

    @Bean
    fun memoryPubSub(): PubSubTopicExchangeService<UUID, String> = MemoryPubSubTopicExchange()
}

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["index"])
class IndexControllers : IndexControllersConfiguration()

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"])
class PersistenceControllers : PersistenceControllersConfiguration()

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["key"])
class KeyControllers : KeyControllersConfiguration()

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"])
class PubSubControllers : PubSubControllerConfiguration()

@ConditionalOnProperty(prefix = "app.service.edge", name = ["messaging"])
@Controller
@MessageMapping("edge.message")
class ExchangeController(
        indexFactory: IndexServiceFactory<UUID, String, IndexSearchRequest>,
        persistenceFactory: PersistenceServiceFactory<UUID, String>,
        pubsub: PubSubTopicExchangeService<UUID, String>,
        reqs: RequestToQueryConverter<UUID, IndexSearchRequest>,
) :
        MessagingController<UUID, String, IndexSearchRequest>(
                indexFactory.messageIndex(),
                persistenceFactory.message(), pubsub, reqs::topicIdToQuery)

@ConditionalOnProperty(prefix = "app.service.edge", name = ["user"])
@Controller
@MessageMapping("edge.user")
class UserController(
        persistenceFactory: PersistenceServiceFactory<UUID, String>,
        indexFactory: IndexServiceFactory<UUID, String, IndexSearchRequest>,
        reqs: RequestToQueryConverter<UUID, IndexSearchRequest>,
) : UserServiceController<UUID, IndexSearchRequest>(persistenceFactory.user(),
        indexFactory.userIndex(),
        reqs::userHandleToQuery)


@ConditionalOnProperty(prefix = "app.service.edge", name = ["topic"])
@Controller
@MessageMapping("edge.topic")
class TopicController(
        persistenceFactory: PersistenceServiceFactory<UUID, String>,
        indexFactory: IndexServiceFactory<UUID, String, IndexSearchRequest>,
        pubsub: PubSubTopicExchangeService<UUID, String>,
        valueCodecs: ValueCodecFactory<String>,
        reqs: RequestToQueryConverter<UUID, IndexSearchRequest>
) :
        TopicServiceController<UUID, String, IndexSearchRequest>(
                persistenceFactory.topic(),
                indexFactory.topicIndex(),
                pubsub,
                persistenceFactory.user(),
                persistenceFactory.membership(),
                indexFactory.membershipIndex(),
                valueCodecs::emptyValue,
                reqs::topicNameToQuery,
                reqs::membershipIdToQuery
        )