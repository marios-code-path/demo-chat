package com.demo.chat.shell

import com.demo.chat.client.rsocket.DefaultRequesterFactory
import com.demo.chat.client.rsocket.RequestMetadata
import com.demo.chat.config.BaseDomainConfiguration
import com.demo.chat.config.client.rsocket.*
import com.demo.chat.config.deploy.actuator.ActuatorRootKeyCapture
import com.demo.chat.config.persistence.memory.KeyGenConfiguration
import com.demo.chat.config.secure.CompositeAuthConfiguration
import com.demo.chat.config.secure.TransportConfiguration
import com.demo.chat.config.deploy.actuator.RootKey
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.secure.transport.TransportFactory
import com.fasterxml.jackson.databind.ObjectMapper
import io.rsocket.metadata.WellKnownMimeType
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.util.MimeTypeUtils
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.pattern.PathPatternRouteMatcher
import reactor.core.publisher.Hooks
import java.util.*

@SpringBootApplication()
@Import(
    RSocketPropertyConfiguration::class,
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
    CompositeAuthConfiguration::class,
    ActuatorRootKeyCapture::class
)
@EnableRSocketSecurity
class BaseApp {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Hooks.onOperatorDebug()
            runApplication<BaseApp>(*args)

        }
    }
}

@Configuration
class ShellStateConfiguration {

    companion object {
        var loggedInUser: Optional<Any> = Optional.empty()
        var loginMetadata: Optional<UsernamePasswordMetadata> = Optional.empty()
    }

    @Bean
    fun securityRequesterFactory(
        builder: RSocketRequester.Builder,
        connection: TransportFactory,
        clientProps: RSocketClientProperties,
    ): DefaultRequesterFactory = DefaultRequesterFactory(
        builder, connection, clientProps
    ) {
        RequestMetadata(
            loginMetadata
                .map { UsernamePasswordMetadata(it.username, it.password) }
                .orElseGet { UsernamePasswordMetadata(Anon::class.java.simpleName, "") },
            MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string)
        )
    }
}

@Configuration
class StrategiesCustomizer : RSocketStrategiesCustomizer {
    override fun customize(strategies: RSocketStrategies.Builder) {
        strategies.apply {
            encoder(SimpleAuthenticationEncoder())
            routeMatcher(PathPatternRouteMatcher())
        }
    }
}
