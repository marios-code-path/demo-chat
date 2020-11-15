package com.demo.chat.deploy.app.memory

import com.demo.chat.codec.EmptyUUIDCodec
import com.demo.chat.controller.edge.MessagingController
import com.demo.chat.controller.service.IndexServiceController
import com.demo.chat.controller.service.KeyServiceController
import com.demo.chat.controller.service.PersistenceServiceController
import com.demo.chat.deploy.config.client.RSocketClientConfiguration
import com.demo.chat.deploy.config.client.RSocketClientFactory
import com.demo.chat.deploy.config.SerializationConfiguration
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.*
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.*

object UUIDCodec : EmptyUUIDCodec()

class AppRSocketClientConfiguration(clients: RSocketClientFactory) : RSocketClientConfiguration<UUID, String>(clients)

@SpringBootApplication
@Import(RSocketClientFactory::class,
        SerializationConfiguration::class,
        AppRSocketClientConfiguration::class)
class App {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }
}

@Profile("key")
@Controller
@MessageMapping("key")
class KeyController : KeyServiceController<UUID>(KeyServiceInMemory(UUIDCodec))

@Profile("message-exchange")
@Configuration
@Import(RSocketClientConfiguration::class)
class MessageExchangeConfiguration : InMemoryMessageExchangeFactory<UUID, String>() {

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
class IndexConfiguration : InMemoryIndexFactory<UUID, String, Map<String, String>>(
        UserIndexEntryEncoder, MessageIndexEntryEncoder, TopicIndexEntryEncoder
) {
    @Controller
    @MessageMapping("user")
    class UserIndexController(s: IndexService<UUID, User<UUID>, Map<String, String>>) : IndexServiceController<UUID, User<UUID>, Map<String, String>>(s)

    @Controller
    @MessageMapping("message")
    class MessageIndexController(s: IndexService<UUID, Message<UUID, String>, Map<String, UUID>>) : IndexServiceController<UUID, Message<UUID, String>, Map<String, UUID>>(s)

    @Controller
    @MessageMapping("topic")
    class TopicIndexController(s: IndexService<UUID, MessageTopic<UUID>, Map<String, String>>) : IndexServiceController<UUID, MessageTopic<UUID>, Map<String, String>>(s)
}

@Profile("persistence")
@Configuration
class PersistenceConfiguration(keyService: IKeyService<UUID>) : InMemoryPersistenceFactory<UUID, String>(keyService) {

    @Controller
    @MessageMapping("user")
    class UserPersistenceController(t: UserPersistence<UUID>) : PersistenceServiceController<UUID, User<UUID>>(t)

    @Controller
    @MessageMapping("message")
    class MessagePersistenceController(t: MessagePersistence<UUID, String>) : PersistenceServiceController<UUID, Message<UUID, String>>(t)

    @Controller
    @MessageMapping("topic")
    class TopicPersistenceController(t: TopicPersistence<UUID>) : PersistenceServiceController<UUID, MessageTopic<UUID>>(t)

    @Controller
    @MessageMapping("membership")
    class MembershipPersistenceController(t: MembershipPersistence<UUID>) : PersistenceServiceController<UUID, TopicMembership<UUID>>(t)
}