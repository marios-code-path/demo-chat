package com.demo.chat.shell

import com.demo.chat.client.rsocket.DefaultRequesterFactory
import com.demo.chat.client.rsocket.RequestMetadata
import com.demo.chat.config.BaseDomainConfiguration
import com.demo.chat.config.client.rsocket.*
import com.demo.chat.config.persistence.memory.KeyGenConfiguration
import com.demo.chat.config.secure.CompositeAuthConfiguration
import com.demo.chat.config.secure.TransportConfiguration
import com.demo.chat.deploy.actuator.RootKey
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.secure.transport.TransportFactory
import com.demo.chat.service.composite.ChatUserService
import com.fasterxml.jackson.databind.ObjectMapper
import io.rsocket.metadata.WellKnownMimeType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
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
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
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
class ShellStateConfiguration {

    companion object {
        var loggedInUser: Optional<Any> = Optional.empty()
        var simpleAuthToken: Optional<UsernamePasswordAuthenticationToken> = Optional
            .of(UsernamePasswordAuthenticationToken(Anon::class.java.simpleName, ""))
    }

    @Bean
    fun <T> captureRootKeys(@Value("\${app.management.server.port}") port: String,
                            typeUtil: TypeUtil<T>,
                            mapper: ObjectMapper,
                            rootKeys: RootKeys<T>): ApplicationListener<ApplicationStartedEvent> =
        ApplicationListener { _ ->
            val exchangeStrategies = ExchangeStrategies.builder()
                .codecs { configurer ->
                    configurer.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(mapper))
                    configurer.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(mapper))
                }
                .build()

            val client = WebClient.builder()
                .exchangeStrategies(exchangeStrategies)
                .baseUrl("http://localhost:${port}")
                .build()

            val result = client.get()
                .uri("/actuator/rootkeys")
                .retrieve()
                .bodyToMono(object : ParameterizedTypeReference<Map<String, RootKey>>() {})
                .block()!!

            result.keys.forEach { key ->
                if(result.containsKey(key)) {
                    val domain = result[key]!!
                    rootKeys.addRootKey(key, Key.funKey(typeUtil.assignFrom(domain.id)))
                }
            }
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
