package com.demo.deploy.app

import com.demo.chat.client.rsocket.KeyClient
import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.IKeyService
import com.demo.deploy.config.JacksonConfiguration
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

        ctx.registerBean(ClientRun::class.java, Supplier {
            ClientRun()
        })

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

class ClientRun {
    val logger = LoggerFactory.getLogger(this::class.java.canonicalName)
    @Bean
    fun <T> keyClient(requester: RSocketRequester.Builder): IKeyService<T> = KeyClient(
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
                .map {exists ->
                    logger.info("Key Exists: $exists")
                }
                .block()

    }

}