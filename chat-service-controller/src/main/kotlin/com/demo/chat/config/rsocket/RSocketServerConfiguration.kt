package com.demo.chat.config.rsocket

import com.demo.chat.domain.knownkey.RootKeys
//import com.demo.chat.secure.service.CoreReactiveAuthenticationManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.rsocket.RSocketMessageHandlerCustomizer
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodArgumentResolver
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.rsocket.RSocketSecurity
import org.springframework.security.messaging.handler.invocation.reactive.AuthenticationPrincipalArgumentResolver
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.web.util.pattern.PathPatternRouteMatcher
import tools.jackson.databind.JacksonModule
import tools.jackson.databind.json.JsonMapper

@Configuration
@ConditionalOnProperty("app.server.proto", havingValue = "rsocket")
class RSocketServerConfiguration<T> {

    // TODO: lock down!
    @Bean
    fun rsocketSecurityAuthentication(
        security: RSocketSecurity,
        rootKeys: RootKeys<T>
    ): PayloadSocketAcceptorInterceptor = security
        .simpleAuthentication(Customizer.withDefaults())
        .addPayloadInterceptor(DefaultingAnonymousPayloadInterceptor(rootKeys))
        .authorizePayload { authorize ->
            authorize
                .setup()
                .permitAll()
                .anyExchange()
                .permitAll()
                .anyRequest()
                .permitAll()
        }
        .build()

    /**
     * The RSocket strategies, including the chat domain Jackson 3 codec.
     *
     * **RSocket carries the same Jackson gap that HTTP carried.** Spring Boot
     * 4 decodes with Jackson 3, and the chat domain deserializers were
     * Jackson 2 only, so a payload of a domain type answered a type
     * definition error. CHAT-qwmjrixq closed that for HTTP through
     * ServerJsonCodecConfiguration, and the RSocket strategies are a separate
     * codec set that needed the same module.
     *
     * The module beans come from ChatJackson3Modules. findAndAddModules would
     * not find them, because that call reads the module service registry
     * rather than the Spring context.
     */
    @Bean
    fun rSocketStrategiesCustomizer(domainModules: List<JacksonModule>): RSocketStrategiesCustomizer {
        val mapper = JsonMapper.builder()
            .addModules(domainModules)
            .build()

        return RSocketStrategiesCustomizer { strategies ->
            strategies.apply {
                encoder(SimpleAuthenticationEncoder())
                decoder(JacksonJsonDecoder(mapper))
                encoder(JacksonJsonEncoder(mapper))
                routeMatcher(PathPatternRouteMatcher())
            }
        }
    }

    @Bean
    fun messageHandlerCustomizer(): RSocketMessageHandlerCustomizer =
        RSocketMessageHandlerCustomizer { messageHandler ->
            val ar: HandlerMethodArgumentResolver = AuthenticationPrincipalArgumentResolver()
            messageHandler.argumentResolverConfigurer.addCustomResolver(ar)
        }
}

