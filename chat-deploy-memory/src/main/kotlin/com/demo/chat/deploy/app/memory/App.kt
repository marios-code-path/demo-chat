package com.demo.chat.deploy.app.memory

import com.demo.chat.controller.core.PersistenceServiceController
import com.demo.chat.deploy.config.core.JacksonConfiguration
import com.demo.chat.deploy.config.client.CoreClientConfiguration
import com.demo.chat.deploy.config.client.CoreClients
import com.demo.chat.deploy.config.client.EdgeClients
import com.demo.chat.deploy.config.client.consul.ConsulRequesterFactory
import com.demo.chat.deploy.config.codec.IndexSearchRequestConverters
import com.demo.chat.deploy.config.codec.RequestToQueryConverters
import com.demo.chat.deploy.config.controllers.core.IndexControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.KeyControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.PersistenceControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.PubSubControllerConfiguration
import com.demo.chat.deploy.config.controllers.edge.*
import com.demo.chat.deploy.config.core.IndexServiceConfiguration
import com.demo.chat.deploy.config.core.KeyServiceConfiguration
import com.demo.chat.deploy.config.core.PersistenceServiceConfiguration
import com.demo.chat.deploy.config.codec.ValueLiterals
import com.demo.chat.deploy.config.properties.AppConfigurationProperties
import com.demo.chat.domain.*
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.PubSubService
import com.demo.chat.service.conflate.IndexedPersistence
import com.demo.chat.service.conflate.PubSubbedPersistence
import com.demo.chat.service.conflate.PublishConfiguration
import com.demo.chat.service.impl.lucene.index.IndexEntryEncoder
import com.demo.chat.service.impl.lucene.index.StringToKeyEncoder
import com.demo.chat.service.impl.memory.messaging.MemoryPubSubTopicExchange
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
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
@Import(
        RSocketRequesterAutoConfiguration::class,
        JacksonConfiguration::class,
        ConsulRequesterFactory::class,
        CoreClients::class,
        EdgeClients::class,
)
class App {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }

    @Configuration
    class ResourceConfiguration {
        @Configuration
        class AppRSocketClientConfiguration(clients: CoreClients)
            : CoreClientConfiguration<UUID, String, IndexSearchRequest>(clients)

        @Configuration
        class MemoryKeyServiceFactory : KeyServiceConfiguration<UUID> {
            override fun keyService() = KeyServiceInMemory { UUID.randomUUID() }
        }

        @Bean
        fun memoryPubSub(): PubSubService<UUID, String> = MemoryPubSubTopicExchange()

        @Configuration
        class PersistenceConfiguration(keyFactory: KeyServiceConfiguration<UUID>)
            : InMemoryPersistenceConfiguration<UUID, String>(keyFactory.keyService())

        @Configuration
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
        fun requestToIndexSearchRequest(): RequestToQueryConverters<UUID, IndexSearchRequest> = IndexSearchRequestConverters()

        @Bean
        fun stringCodecFactory(): ValueLiterals<String> =
                object : ValueLiterals<String> {
                    override fun emptyValue() = ""
                    override fun fromString(t: String) = t
                }
    }

    @ConditionalOnBean(PersistenceControllersConfiguration.UserPersistenceController::class)
    @Bean
    fun commandRunner(): ApplicationRunner = ApplicationRunner {
        println("THE Persistence/Controllers was present")
    }
}

//TODO: I want to expose controllers as groups of controllers.
//TODO: Skeptic in me says @configuration and @conditionalonproperty dont even matter.
//TODO: declare an 'onRegistered' callback on controller components.
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
        indexConfig: IndexServiceConfiguration<UUID, String, IndexSearchRequest>,
        persistenceConfig: PersistenceServiceConfiguration<UUID, String>,
        pubsub: PubSubService<UUID, String>,
        reqs: RequestToQueryConverters<UUID, IndexSearchRequest>,
) :
        ExchangeControllerConfig<UUID, String, IndexSearchRequest>(
                indexConfig,
                persistenceConfig, pubsub, reqs)

class MembershipConflation(
        persistenceConfig: PersistenceServiceConfiguration<UUID, String>,
        indexConfig: IndexServiceConfiguration<UUID, String, IndexSearchRequest>,
        pubsub: PubSubService<UUID, TopicMembership<UUID>>,
) : PersistenceServiceController<UUID, TopicMembership<UUID>>(
        PubSubbedPersistence(
                IndexedPersistence(persistenceConfig.membership(), indexConfig.membershipIndex()),
                pubsub,
                PublishConfiguration.create(add = true, rem = true)
        ) {
            Message.create(
                    MessageKey.create(
                            it.key,
                            it.member,
                            it.memberOf
                    ),
                    it,
                    false
            )
        }
)

@ConditionalOnProperty(prefix = "app.service.edge", name = ["user"])
@Controller
@MessageMapping("edge.user")
class UserController(
        persistenceConfig: PersistenceServiceConfiguration<UUID, String>,
        indexConfig: IndexServiceConfiguration<UUID, String, IndexSearchRequest>,
        reqs: RequestToQueryConverters<UUID, IndexSearchRequest>,
) : UserControllerConfiguration<UUID, String, IndexSearchRequest>(persistenceConfig, indexConfig, reqs)

@ConditionalOnProperty(prefix = "app.service.edge", name = ["topic"])
@Controller
@MessageMapping("edge.topic")
class TopicController(
        persistenceConfig: PersistenceServiceConfiguration<UUID, String>,
        indexConfig: IndexServiceConfiguration<UUID, String, IndexSearchRequest>,
        pubsub: PubSubService<UUID, String>,
        valueCodecs: ValueLiterals<String>,
        reqs: RequestToQueryConverters<UUID, IndexSearchRequest>,
) :
        TopicControllerConfiguration<UUID, String, IndexSearchRequest>(
                persistenceConfig,
                indexConfig,
                pubsub,
                valueCodecs,
                reqs
        )