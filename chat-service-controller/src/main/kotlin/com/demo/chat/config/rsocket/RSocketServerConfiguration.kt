package com.demo.chat.config.rsocket

import com.demo.chat.domain.knownkey.RootKeys
//import com.demo.chat.secure.service.CoreReactiveAuthenticationManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.rsocket.autoconfigure.RSocketMessageHandlerCustomizer
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodArgumentResolver
import org.springframework.security.config.Customizer
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.security.config.annotation.rsocket.RSocketSecurity
import org.springframework.security.messaging.handler.invocation.reactive.AuthenticationPrincipalArgumentResolver
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
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
     * The server RSocket strategies, including the chat domain Jackson 3 codec.
     *
     * **The server decodes the request payload and needs the domain module.**
     * Without it a Key in a request answers an RSocket application error
     * 0x201 with a type definition error. 15 tests in chat-client-rsocket
     * measured that before this bean carried the module.
     *
     * The decoder is added **before** the default one. Spring picks the first
     * decoder that matches a type, so an appended decoder never runs. That
     * detail cost one wasted repair earlier.
     *
     * **The CBOR path is deliberately not wired here.**
     * TargetIdentifierInterceptor resolves its decoder for
     * MediaType.APPLICATION_CBOR, no test reaches it, and it gets its own
     * reading before it gets a change. See CHAT-qwmjrixq.
     */
    @Bean
    fun rSocketStrategiesCustomizer(domainModules: List<JacksonModule>): RSocketStrategiesCustomizer {
        val mapper = JsonMapper.builder()
            .addModules(domainModules)
            .build()

        return RSocketStrategiesCustomizer { strategies ->
            strategies.apply {
                encoder(SimpleAuthenticationEncoder())
                decoders { it.add(0, JacksonJsonDecoder(mapper)) }
                encoders { it.add(0, JacksonJsonEncoder(mapper)) }
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

