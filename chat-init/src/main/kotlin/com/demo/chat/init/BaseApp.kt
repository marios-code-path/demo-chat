package com.demo.chat.init

import com.demo.chat.client.rsocket.DefaultRequesterFactory
import com.demo.chat.config.BaseDomainConfiguration
import com.demo.chat.config.client.rsocket.ClientConfiguration
import com.demo.chat.config.client.rsocket.CompositeClientsConfiguration
import com.demo.chat.config.client.rsocket.CoreClientsConfiguration
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.config.persistence.memory.KeyGenConfiguration
import com.demo.chat.config.secure.CompositeAuthConfiguration
import com.demo.chat.config.secure.TransportConfiguration
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import reactor.core.publisher.Hooks

@SpringBootApplication()
@EnableConfigurationProperties(RSocketClientProperties::class)
@Import(
    // Serialization
    JacksonAutoConfiguration::class,
    DefaultChatJacksonModules::class,
    RSocketStrategiesAutoConfiguration::class,
    RSocketMessagingAutoConfiguration::class,
    // TYPES
    BaseDomainConfiguration::class,
    // Transport Security
    TransportConfiguration::class,
    // Services
    KeyGenConfiguration::class,
    DefaultRequesterFactory::class,
    ClientConfiguration::class,
    CoreClientsConfiguration::class,
    CompositeClientsConfiguration::class,
    CompositeAuthConfiguration::class
)
class BaseApp {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Hooks.onOperatorDebug()
            runApplication<BaseApp>(*args)

        }
    }
}