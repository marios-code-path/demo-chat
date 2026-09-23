package com.demo.chat.config.rsocket

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

    /**
     * The RSocket security chain.
     *
     * **`anonymous` is what gives an unauthenticated caller an identity.**
     * Without it a payload that carries no credential reaches the service
     * layer with no security context, and `ContextIdentity` answers no
     * identity, which means denied.
     *
     * This replaced `DefaultingAnonymousPayloadInterceptor`, which installed
     * a token of its own. That token was measured on 2026-09-23 and it never
     * reached the reader, because the interceptor ran before any context
     * existed. Spring Security supplies this seam, so the application no
     * longer states it. See `docs/IDENTITY-POLICY.md`.
     *
     * `rootKeys` is no longer a parameter. `ContextIdentity` maps the
     * anonymous token to the `Anon` root key at the read.
     */
    // TODO: lock down!
    @Bean
    fun rsocketSecurityAuthentication(
        security: RSocketSecurity
    ): PayloadSocketAcceptorInterceptor = security
        .simpleAuthentication(Customizer.withDefaults())
        .anonymous(Customizer.withDefaults())
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
     * **No CBOR decoder is registered, and nothing asks for one.**
     * The one reader of MediaType.APPLICATION_CBOR was a spike that derived
     * a target from the payload. The owner replaced that idea with
     * SpringSecurityAccessBrokerService and removed the spike.
     * See CHAT-bgsqwjph.
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

