package com.demo.chat.deploy.app.memory

import com.demo.chat.codec.EmptyUUIDCodec
import com.demo.chat.controller.service.KeyServiceController
import com.demo.chat.controller.service.PersistenceServiceController
import com.demo.chat.deploy.config.PersistenceClientFactory
import com.demo.chat.deploy.config.SerializationConfiguration
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.*
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

@Profile("persistence")
@Configuration
class PersistenceController(keyService: IKeyService<UUID>) : InMemoryPersistenceFactory<UUID>(keyService) {

    @Profile("user")
    @Controller
    @MessageMapping("user")
    class UserPersistenceRSocket(t: UserPersistence<UUID>) : PersistenceServiceController<UUID, User<UUID>>(t)

    @Profile("message")
    @Controller
    @MessageMapping("message")
    class MessagePersistenceRSocket(t: MessagePersistence<UUID, String>) : PersistenceServiceController<UUID, Message<UUID, String>>(t)

    @Profile("topic")
    @Controller
    @MessageMapping("topic")
    class TopicPersistenceRSocket(t: TopicPersistence<UUID>) : PersistenceServiceController<UUID, MessageTopic<UUID>>(t)

    @Profile("membership")
    @Controller
    @MessageMapping("membership")
    class MembershipPersistenceRSocket(t: MembershipPersistence<UUID>) : PersistenceServiceController<UUID, TopicMembership<UUID>>(t)
}