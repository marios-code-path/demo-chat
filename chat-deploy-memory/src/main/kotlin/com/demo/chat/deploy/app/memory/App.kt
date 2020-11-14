package com.demo.chat.deploy.app.memory

import java.util.function.Function
import com.demo.chat.codec.EmptyUUIDCodec
import com.demo.chat.controller.edge.MessagingController
import com.demo.chat.controller.service.KeyServiceController
import com.demo.chat.controller.service.PersistenceServiceController
import com.demo.chat.deploy.config.PersistenceClientFactory
import com.demo.chat.deploy.config.SerializationConfiguration
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.*
import com.demo.chat.service.impl.memory.index.IndexerFn
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.*

object UUIDCodec : EmptyUUIDCodec()

@SpringBootApplication
@Import(PersistenceClientFactory::class,
        SerializationConfiguration::class)
class App {
    @Profile("key-client")
    @Bean
    fun keyClient(clientFactory: PersistenceClientFactory): IKeyService<UUID> = clientFactory.keyClient()

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }
}

@Profile("key-service")
@Controller
@MessageMapping("key")
class KeyController : KeyServiceController<UUID>(KeyServiceInMemory(UUIDCodec))

@Profile("message-exchange")
@Configuration
class MessageExchangeConfiguration : InMemoryMessagingFactory<UUID, String>() {

    @Controller
    @MessageMapping("xg")
    class MessageExchangeController(
            i: MessageIndexService<UUID, String>,
            p: MessagePersistence<UUID, String>,
            x: PubSubTopicExchangeService<UUID, String>)
        : MessagingController<UUID, String>(i, p, x)
}

@Profile("index")
@Configuration
class IndexConfiguration() : InMemoryIndexFactory<UUID, String, Map<String, String>>(
        UserIndexerFn, MessageIndexerFn, TopicIndexerFn
)

@Profile("persistence")
@Configuration
class PersistenceConfiguration(keyService: IKeyService<UUID>) : InMemoryPersistenceFactory<UUID, String>(keyService) {

    @Profile("user")
    @Controller
    @MessageMapping("user")
    class UserPersistenceController(t: UserPersistence<UUID>) : PersistenceServiceController<UUID, User<UUID>>(t)

    @Profile("message")
    @Controller
    @MessageMapping("message")
    class MessagePersistenceController(t: MessagePersistence<UUID, String>) : PersistenceServiceController<UUID, Message<UUID, String>>(t)

    @Profile("topic")
    @Controller
    @MessageMapping("topic")
    class TopicPersistenceController(t: TopicPersistence<UUID>) : PersistenceServiceController<UUID, MessageTopic<UUID>>(t)

    @Profile("membership")
    @Controller
    @MessageMapping("membership")
    class MembershipPersistenceController(t: MembershipPersistence<UUID>) : PersistenceServiceController<UUID, TopicMembership<UUID>>(t)
}