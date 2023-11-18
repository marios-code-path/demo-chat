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
import org.springframework.web.util.pattern.PathPatternRouteMatcher

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

    @Bean
    fun rSocketStrategiesCustomizer(): RSocketStrategiesCustomizer =
        RSocketStrategiesCustomizer { strategies ->
            strategies.apply {
                encoder(SimpleAuthenticationEncoder())
                routeMatcher(PathPatternRouteMatcher())
            }
        }

    @Bean
    fun messageHandlerCustomizer(): RSocketMessageHandlerCustomizer =
        RSocketMessageHandlerCustomizer { messageHandler ->
            val ar: HandlerMethodArgumentResolver = AuthenticationPrincipalArgumentResolver()
            messageHandler.argumentResolverConfigurer.addCustomResolver(ar)
        }
}

