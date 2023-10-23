package com.demo.chat.config.rsocket

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.secure.CompositeAuthConfiguration
import com.demo.chat.secure.service.CoreAuthenticationManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.rsocket.RSocketMessageHandlerCustomizer
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodArgumentResolver
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.authentication.AnonymousAuthenticationProvider
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.rsocket.RSocketSecurity
import org.springframework.security.messaging.handler.invocation.reactive.AuthenticationPrincipalArgumentResolver
import org.springframework.security.rsocket.api.PayloadExchange
import org.springframework.security.rsocket.authorization.AuthorizationPayloadInterceptor
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.web.util.pattern.PathPatternRouteMatcher
import reactor.core.publisher.Mono

@Configuration
@ConditionalOnProperty("app.server.proto", havingValue = "rsocket")
class RSocketServerConfiguration<T>(
    private val coreBeans: PersistenceServiceBeans<T, *>,
    private val beans: CompositeAuthConfiguration<T, *>
) {

    @Bean
    fun authMan(): ReactiveAuthenticationManager =
         CoreAuthenticationManager(
            beans.authenticationService(),
            coreBeans.userPersistence()
        )

    // TODO: lock down!
    @Bean
    fun rsocketSecurityAuthentication(security: RSocketSecurity, authMan: ReactiveAuthenticationManager)
            : PayloadSocketAcceptorInterceptor = security
        .simpleAuthentication(Customizer.withDefaults())
        .authenticationManager(authMan)
        .authorizePayload { authorize ->
            authorize
                .setup()
                .permitAll()
                .anyExchange()
                .authenticated()
                .anyRequest()
                .authenticated()
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

class CustomAuthorizationPayloadInterceptor(
    private val authenticationManager: ReactiveAuthenticationManager
) : AuthorizationPayloadInterceptor(authenticationManager, RSocketStrategies.builder().build()) {

    override fun authenticate(payloadExchange: PayloadExchange): Mono<Authentication> {
        // Extract metadata from payloadExchange and convert to Authentication
        // If no metadata found, return anonymous token
        val metadata: Metadata? = extractMetadata(payloadExchange)
        return if (metadata == null) {
            Mono.just(AnonymousAuthenticationToken())
        } else {
            super.authenticate(payloadExchange)
        }
    }

    private fun extractMetadata(payloadExchange: PayloadExchange): Metadata? {
        // Logic to extract metadata
        // Return null if no metadata is found
    }
}
