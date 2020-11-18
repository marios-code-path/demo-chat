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
import com.demo.chat.domain.*
import com.demo.chat.service.*
import com.demo.chat.service.impl.memory.index.IndexEntryEncoder
import com.demo.chat.service.impl.memory.index.StringToKeyEncoder
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
import org.springframework.context.annotation.Profile
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.*

object UUIDCodec : EmptyUUIDCodec()

@ConfigurationProperties("app.client.rsocket")
@ConstructorBinding
class AppRSocketClientProperties(override val key: String = "",
                              override val index: String = "",
                              override val persistence: String = "",
                              override val messaging: String = "") : RSocketClientProperties

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

}

@ConditionalOnProperty(prefix="app.service", name = ["key"])
@Controller
@MessageMapping("key")
class KeyController : KeyServiceController<UUID>(KeyServiceInMemory(UUIDCodec))


@Configuration
@ConditionalOnProperty(prefix="app.service", name = ["message"])
class MessageExchangeConfiguration : InMemoryMessageExchangeConfiguration<UUID, String>() {

    @Controller
    @MessageMapping("message")
    @ConditionalOnProperty(prefix="app.service", name = ["message"])
    class MessageExchangeController(
            i: MessageIndexService<UUID, String>,
            p: MessagePersistence<UUID, String>,
            x: PubSubTopicExchangeService<UUID, String>,
    )
        : MessagingController<UUID, String>(i, p, x)
}

@Configuration
@ConditionalOnProperty(prefix="app.service", name = ["index"])
class IndexConfiguration : InMemoryIndexConfiguration<UUID, String, Map<String, String>>(
        StringToKeyEncoder { i -> Key.funKey(UUID.fromString(i)) },
        IndexEntryEncoder<User<UUID>> { t ->
            listOf(
                    Pair("key", t.key.id.toString()),
                    Pair("handle", t.handle),
                    Pair("name", t.name)
            )
        },
        IndexEntryEncoder<Message<UUID, String>> { t ->
            listOf(
                    Pair("key", t.key.id.toString()),
                    Pair("text", t.data)
            )
        },
        IndexEntryEncoder<MessageTopic<UUID>> { t ->
            listOf(
                    Pair("key", t.key.id.toString()),
                    Pair("name", t.data)
            )
        }
) {
    @Controller
    @ConditionalOnProperty(prefix="app.service", name = ["index", "index.user"])
    @MessageMapping("user")
    class UserIndexController(s: IndexService<UUID, User<UUID>, Map<String, String>>) : IndexServiceController<UUID, User<UUID>, Map<String, String>>(s)

    @Controller
    @ConditionalOnProperty(prefix="app.service", name = ["index", "index.message"])
    @MessageMapping("message")
    class MessageIndexController(s: IndexService<UUID, Message<UUID, String>, Map<String, UUID>>) : IndexServiceController<UUID, Message<UUID, String>, Map<String, UUID>>(s)

    @Controller
    @ConditionalOnProperty(prefix="app.service", name = ["index", "index.topic"])
    @MessageMapping("topic")
    class TopicIndexController(s: IndexService<UUID, MessageTopic<UUID>, Map<String, String>>) : IndexServiceController<UUID, MessageTopic<UUID>, Map<String, String>>(s)
}

@Configuration
@ConditionalOnProperty(prefix="app.service", name = ["persistence"])
class PersistenceConfiguration(keyService: IKeyService<UUID>) : InMemoryPersistenceConfiguration<UUID, String>(keyService) {

    @Controller
    @ConditionalOnProperty(prefix="app.service", name = ["persistence"])
    @MessageMapping("user")
    class UserPersistenceController(t: UserPersistence<UUID>) : PersistenceServiceController<UUID, User<UUID>>(t)

    @Controller
    @ConditionalOnProperty(prefix="app.service", name = ["persistence"])
    @MessageMapping("message")
    class MessagePersistenceController(t: MessagePersistence<UUID, String>) : PersistenceServiceController<UUID, Message<UUID, String>>(t)

    @Controller
    @ConditionalOnProperty(prefix="app.service", name = ["persistence"])
    @MessageMapping("topic")
    class TopicPersistenceController(t: TopicPersistence<UUID>) : PersistenceServiceController<UUID, MessageTopic<UUID>>(t)

    @Controller
    @ConditionalOnProperty(prefix="app.service", name = ["persistence"])
    @MessageMapping("membership")
    class MembershipPersistenceController(t: MembershipPersistence<UUID>) : PersistenceServiceController<UUID, TopicMembership<UUID>>(t)
}