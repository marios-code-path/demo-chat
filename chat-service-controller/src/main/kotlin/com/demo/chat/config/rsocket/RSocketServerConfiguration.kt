package com.demo.chat.config.rsocket

import com.demo.chat.controller.resolve.VerifiedKeyArgumentResolver
import com.demo.chat.service.core.KeyVerifier
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.rsocket.autoconfigure.RSocketMessageHandlerCustomizer
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodArgumentResolver
import org.springframework.security.messaging.handler.invocation.reactive.AuthenticationPrincipalArgumentResolver
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.web.util.pattern.PathPatternRouteMatcher
import tools.jackson.databind.JacksonModule
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("app.server.proto", havingValue = "rsocket")
class RSocketServerConfiguration<T> {

    @Bean
    fun requireRSocketSecurity(
        environment: Environment,
        securityInterceptor: ObjectProvider<PayloadSocketAcceptorInterceptor>,
    ): RSocketSecurityRequired {
        check(environment.getProperty("app.service.composite.auth") == "true") {
            "An RSocket server requires app.service.composite.auth=true."
        }
        securityInterceptor.getIfAvailable()
            ?: throw IllegalStateException(
                "An RSocket server requires app.service.composite.auth=true."
            )
        return RSocketSecurityRequired
    }

    @Bean
    fun rSocketStrategiesCustomizer(domainModules: List<JacksonModule>): RSocketStrategiesCustomizer {
        val mapper = JsonMapper.builder().addModules(domainModules).build()
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
    fun messageHandlerCustomizer(verifiers: ObjectProvider<KeyVerifier<*>>): RSocketMessageHandlerCustomizer =
        RSocketMessageHandlerCustomizer { messageHandler ->
            val principalResolver: HandlerMethodArgumentResolver = AuthenticationPrincipalArgumentResolver()
            messageHandler.argumentResolverConfigurer.addCustomResolver(principalResolver)
            messageHandler.argumentResolverConfigurer.addCustomResolver(
                VerifiedKeyArgumentResolver(messageHandler.decoders, verifiers)
            )
        }
}

object RSocketSecurityRequired
