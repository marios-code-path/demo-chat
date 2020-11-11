package com.demo.chat.deploy.app

import com.demo.chat.ByHandleRequest
import com.demo.chat.UserCreateRequest
import com.demo.chat.client.rsocket.KeyClient
import com.demo.chat.client.rsocket.MessagePersistenceClient
import com.demo.chat.client.rsocket.UserPersistenceClient
import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.User
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.IKeyService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.deploy.config.JacksonConfiguration
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.context.support.GenericApplicationContext
import org.springframework.messaging.rsocket.RSocketRequester
import java.util.*
import java.util.function.Supplier

@Deprecated("Move me to tests; You shouldn't run tests in production!")
@Profile("test-client")
@SpringBootConfiguration
class TestClient : ApplicationContextInitializer<GenericApplicationContext> {
    override fun initialize(ctx: GenericApplicationContext) {
        ctx.registerBean(JacksonModules::class.java, Supplier {
            JacksonModules(JsonNodeAnyCodec, JsonNodeAnyCodec)
        })
        ctx.registerBean(JacksonConfiguration::class.java, Supplier {
            JacksonConfiguration()
        }) // Use JacksonAutoconfiguration for CBOR, other encodings.

        ctx.registerBean(RSocketStrategiesAutoConfiguration::class.java, Supplier {
            RSocketStrategiesAutoConfiguration()
        })
        ctx.registerBean(RSocketRequesterAutoConfiguration::class.java, Supplier {
            RSocketRequesterAutoConfiguration()
        })

        ctx.environment.activeProfiles.forEach { profile ->
            when (profile) {
                "user" -> {
                    ctx.registerBean(ClientUserRun::class.java, Supplier {
                        ClientUserRun()
                    })
                }
                "key" -> {
                    ctx.registerBean(ClientKeyRun::class.java, Supplier {
                        ClientKeyRun()
                    })
                }
                "message" -> {
                    ctx.registerBean(ClientMessageRun::class.java, Supplier {
                        ClientMessageRun()
                    })
                }
                "edge" -> {
                    ctx.registerBean(EdgeRun::class.java, Supplier {
                        EdgeRun()
                    })
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplicationBuilder(TestClient::class.java)
                    .web(WebApplicationType.NONE)
                    .initializers(TestClient())
                    .run(*args)
        }
    }
}

class EdgeRun {
    val logger = LoggerFactory.getLogger(this::class.java.canonicalName)

    @Bean
    fun run(builder: RSocketRequester.Builder): ApplicationRunner = ApplicationRunner { appArgs ->
        val requester = builder
                .connectTcp("localhost", 6503)
                .block()!!

        requester
                .route("edge.user.user-add")
                .data(UserCreateRequest("MG", "1002", "JPG"))
                .retrieveMono(UUID::class.java)
                .doOnNext {
                    logger.info("UUID FOUND: $it")
                }
                .flatMap {
                    requester
                            .route("edge.user.user-by-handle")
                            .data(ByHandleRequest("1001"))
                            .retrieveMono(User::class.java)
                }
                .doOnNext {
                    logger.info("User Found ${it.key.id}: ${it.handle} / ${it.name}")
                }
                .block()
    }
}

class ClientMessageRun {
    val logger = LoggerFactory.getLogger(this::class.java.canonicalName)

    @Bean
    fun <T, V> userClient(requester: RSocketRequester.Builder): PersistenceStore<T, Message<T, V>> = MessagePersistenceClient(
            requester
                    .connectTcp("localhost", 6501)
                    .block()!!)

    @Bean
    fun run(svc: MessagePersistenceClient<UUID, String>): ApplicationRunner = ApplicationRunner {
        svc.key()
                .flatMap {
                    logger.info("NEW MESSAGE KEY: ${it.id}")
                    svc.add(Message.create(
                            MessageKey.create(it.id, UUID.randomUUID(), UUID.randomUUID()),
                            "HELLO", true
                    ))
                }
                .block()
    }
}

class ClientUserRun {
    val logger = LoggerFactory.getLogger(this::class.java.canonicalName)

    @Bean
    fun <T> userClient(requester: RSocketRequester.Builder): PersistenceStore<T, User<T>> = UserPersistenceClient(
            requester
                    .connectTcp("localhost", 6501)
                    .block()!!)

    @Bean
    fun run(svc: UserPersistenceClient<UUID>): ApplicationRunner = ApplicationRunner {
        svc.key()
                .flatMap {
                    logger.info("NEW USER KEY: ${it.id}")
                    svc.add(User.create(it, "MARIO", "darkbit1001", "http://localhost:8080/image.jpg"))
                }
                .block()
    }
}

class ClientKeyRun {
    val logger = LoggerFactory.getLogger(this::class.java.canonicalName)

    @Bean
    fun <T> keyClient(requester: RSocketRequester.Builder): IKeyService<T> = KeyClient("key.",
            requester
                    .connectTcp("localhost", 6500)
                    .block()!!)

    @Bean
    fun run(svc: IKeyService<UUID>): ApplicationRunner = ApplicationRunner {
        svc.key(UUID::class.java)
                .doOnNext {
                    logger.info("KEY FOUND: ${it.id}")
                }
                .flatMap {
                    svc.exists(it)
                }
                .map { exists ->
                    logger.info("Key Exists: $exists")
                }
                .block()

    }

}