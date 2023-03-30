package com.demo.chat.shell

import com.demo.chat.client.discovery.PropertiesBasedDiscovery
import com.demo.chat.client.rsocket.RSocketRequesterFactory
import com.demo.chat.client.rsocket.RequestMetadata
import com.demo.chat.client.rsocket.transport.RSocketClientTransportFactory
import com.demo.chat.config.BaseDomainConfiguration
import com.demo.chat.config.client.rsocket.*
import com.demo.chat.config.client.discovery.ClientDiscoveryConfiguration
import com.demo.chat.config.deploy.init.HttpRootKeyConsumer
import com.demo.chat.config.persistence.memory.KeyGenConfiguration
import com.demo.chat.config.secure.CompositeAuthConfiguration
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.service.client.ClientDiscovery
import com.demo.chat.service.client.ClientFactory
import io.rsocket.metadata.WellKnownMimeType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.util.MimeTypeUtils
import org.springframework.web.util.pattern.PathPatternRouteMatcher
import reactor.core.publisher.Hooks
import java.util.*

@SpringBootApplication(scanBasePackages = ["com.demo.chat.config", "com.demo.chat.shell.commands"])
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
    RSocketClientTransportConfiguration::class,
    // Services
    KeyGenConfiguration::class,
    RSocketClientConfiguration::class,
    CoreClientsConfiguration::class,
    CompositeClientsConfiguration::class,
    CompositeAuthConfiguration::class,
    HttpRootKeyConsumer::class,
    ClientDiscoveryConfiguration::class
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
    fun propertiesBasedDiscovery(clientProps: RSocketClientProperties): ClientDiscovery =
        PropertiesBasedDiscovery(clientProps)

    @Bean
    fun securityRequesterFactory(
        builder: RSocketRequester.Builder,
        connection: RSocketClientTransportFactory,
        discovery: ClientDiscovery,
    ): ClientFactory<RSocketRequester> = RSocketRequesterFactory(
        discovery, builder, connection
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