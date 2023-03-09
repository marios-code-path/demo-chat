package com.demo.chat.shell

import com.demo.chat.client.rsocket.DefaultRequesterFactory
import com.demo.chat.client.rsocket.RequestMetadata
import com.demo.chat.config.BaseDomainConfiguration
import com.demo.chat.config.client.rsocket.ClientConfiguration
import com.demo.chat.config.client.rsocket.CompositeClientsConfiguration
import com.demo.chat.config.client.rsocket.CoreClientsConfiguration
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.config.persistence.memory.KeyGenConfiguration
import com.demo.chat.config.secure.CompositeAuthConfiguration
import com.demo.chat.config.secure.TransportConfiguration
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.secure.transport.TransportFactory
import io.rsocket.metadata.WellKnownMimeType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.util.MimeTypeUtils
import org.springframework.web.util.pattern.PathPatternRouteMatcher
import reactor.core.publisher.Hooks
import java.util.*

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
@ConditionalOnProperty("app.client.rsocket.discovery.springsecurity")
class SpringSecurityRequesterFactory {

    companion object {
        var authMetadata: Optional<UsernamePasswordAuthenticationToken> = Optional.empty()
    }

    @Bean
    fun securityRequesterFactory(
        builder: RSocketRequester.Builder,
        connection: TransportFactory,
        clientProps: RSocketClientProperties,
    ): DefaultRequesterFactory = DefaultRequesterFactory(builder, connection, clientProps
    ) {
        if(authMetadata.isPresent) {
            RequestMetadata(
                authMetadata.get(),
                MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string))
        } else {
            RequestMetadata(
                UsernamePasswordMetadata("Anon", "nopass"),
                MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string))
        }


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
