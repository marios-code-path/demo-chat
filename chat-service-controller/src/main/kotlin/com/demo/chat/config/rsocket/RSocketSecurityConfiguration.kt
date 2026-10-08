package com.demo.chat.config.rsocket

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.rsocket.server.RSocketServerCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.rsocket.RSocketSecurity
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("app.server.proto", havingValue = "rsocket")
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"], havingValue = "true")
class RSocketSecurityConfiguration {

    @Bean
    fun rsocketSecurityErrorInterceptor() = RSocketSecurityErrorInterceptor()

    @Bean
    fun rsocketSecurityAuthentication(
        security: RSocketSecurity,
        authenticationManager: ReactiveAuthenticationManager,
    ): PayloadSocketAcceptorInterceptor = security
        .addPayloadInterceptor(UnsupportedCredentialRefusal())
        .simpleAuthentication { it.authenticationManager(authenticationManager) }
        .anonymous(Customizer.withDefaults())
        .authorizePayload { authorize ->
            authorize
                .setup().permitAll()
                .route("persist.**").hasAnyRole(SERVICE, ADMIN)
                .route("index.**").hasAnyRole(SERVICE, ADMIN)
                .route("pubsub.**").hasAnyRole(SERVICE, ADMIN)
                .route("secrets.**").hasAnyRole(SERVICE, ADMIN)
                .route("key.key").hasAnyRole(SERVICE, ADMIN)
                .route("key.rem").hasAnyRole(SERVICE, ADMIN)
                .route("key.register").hasAnyRole(SERVICE, ADMIN)
                .anyExchange().permitAll()
        }
        .build()

    @Bean
    fun rsocketSecurityServerCustomizer(
        securityInterceptor: PayloadSocketAcceptorInterceptor,
        errorInterceptor: RSocketSecurityErrorInterceptor,
    ): RSocketServerCustomizer = RSocketServerCustomizer { server ->
        server.interceptors { interceptors ->
            interceptors.forSocketAcceptor(securityInterceptor)
            interceptors.forResponder(errorInterceptor)
        }
    }

    private companion object {
        const val SERVICE = "SERVICE"
        const val ADMIN = "ADMIN"
    }
}
