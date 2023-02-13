package com.demo.chat.init

import com.demo.chat.client.rsocket.DefaultRequesterFactory
import com.demo.chat.client.rsocket.RequesterFactory
import com.demo.chat.config.TypeUtilConfiguration
import com.demo.chat.config.client.rsocket.ClientConfiguration
import com.demo.chat.config.client.rsocket.CompositeClientsConfiguration
import com.demo.chat.config.client.rsocket.CoreClientsConfiguration
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.config.persistence.memory.KeyGenConfiguration
import com.demo.chat.config.secure.AuthConfiguration
import com.demo.chat.config.secure.TransportConfiguration
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.init.domain.BootstrapProperties
import com.demo.chat.secure.transport.TransportFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketRequester

@SpringBootApplication
@EnableConfigurationProperties(BootstrapProperties::class)
@Import(
    // Serialization
    JacksonAutoConfiguration::class,
    DefaultChatJacksonModules::class,
    RSocketStrategiesAutoConfiguration::class,
    RSocketMessagingAutoConfiguration::class,
    // TYPES
    TypeUtilConfiguration::class,
    // Transport Security
    TransportConfiguration::class,
    // Services
    KeyGenConfiguration::class,
    ClientConfiguration::class,
    CoreClientsConfiguration::class,
    CompositeClientsConfiguration::class,
    AuthConfiguration::class,
)
class BaseApp {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<InitApp>(*args)
        }
    }

    @Bean
    @ConditionalOnMissingBean(RequesterFactory::class)
    fun requesterFactory(
        builder: RSocketRequester.Builder,
        connection: TransportFactory,
        rSocketClientProperties: RSocketClientProperties
    ): RequesterFactory = DefaultRequesterFactory(builder, connection, rSocketClientProperties)
}