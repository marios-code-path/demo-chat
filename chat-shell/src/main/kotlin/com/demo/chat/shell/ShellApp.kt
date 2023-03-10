package com.demo.chat.shell

import com.demo.chat.client.rsocket.DefaultRequesterFactory
import com.demo.chat.client.rsocket.RequestMetadata
import com.demo.chat.config.BaseDomainConfiguration
import com.demo.chat.config.client.rsocket.*
import com.demo.chat.config.persistence.memory.KeyGenConfiguration
import com.demo.chat.config.secure.CompositeAuthConfiguration
import com.demo.chat.config.secure.TransportConfiguration
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.secure.transport.TransportFactory
import com.demo.chat.service.composite.ChatUserService
import io.rsocket.metadata.WellKnownMimeType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.util.MimeTypeUtils
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


    @Bean // TODO fetch rootkeys from server
    fun <T> anonymousUser(userService: ChatUserService<T>): ApplicationListener<ApplicationStartedEvent> =
        ApplicationListener { _ ->
            val anon = userService.findByUsername(ByStringRequest(Anon::class.java.simpleName)).blockLast()
            ShellStateConfiguration.loggedInUser = Optional.of(anon?.key?.id as Any)
        }

    @Bean
    fun captureRootKeys(): ApplicationListener<ApplicationStartedEvent> =
        ApplicationListener { _ ->
           val client = WebClient.create("http://localhost:8080")
            val rootKeys = client.get()
                .uri("/actuator/rootkeys")
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()

           //ShellStateConfiguration.rootKeys = Optional.ofNullable(rootKeys)
           ShellStateConfiguration.rootKeys = Optional.of(RootKeys())
        }
}

@Configuration
class ShellStateConfiguration {

    companion object {
        var rootKeys: Optional<RootKeys<Any>> = Optional.empty()
        var loggedInUser: Optional<Any> = Optional.empty()
        var simpleAuthToken: Optional<UsernamePasswordAuthenticationToken> = Optional
            .of(UsernamePasswordAuthenticationToken(Anon::class.java.simpleName, ""))
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
            simpleAuthToken
                .map { UsernamePasswordMetadata(it.name, it.credentials.toString()) }
                .get(),
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
