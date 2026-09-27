package com.demo.chat.test.rsocket

import tools.jackson.databind.json.JsonMapper

import org.springframework.http.codec.json.JacksonJsonEncoder

import org.springframework.http.codec.json.JacksonJsonDecoder

import com.demo.chat.config.ChatJackson3Modules

import org.springframework.boot.rsocket.autoconfigure.RSocketMessageHandlerCustomizer

import org.springframework.beans.factory.ObjectProvider

import com.demo.chat.test.key.TestVerifiers

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.controller.resolve.VerifiedKeyArgumentResolver

import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity
import org.springframework.security.config.annotation.rsocket.RSocketSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.web.util.pattern.PathPatternRouteMatcher

@EnableRSocketSecurity
@EnableReactiveMethodSecurity
@TestConfiguration
class RSocketSecurityTestConfiguration {


    @Bean
    fun rsocketSecurityAuthentication(security: RSocketSecurity)
            : PayloadSocketAcceptorInterceptor = security
        .simpleAuthentication(Customizer.withDefaults())
        .authorizePayload { authorize ->
            authorize
                .setup()
                .permitAll() // This 'setup' access works on connect!
                .anyExchange()
                .permitAll()
                .anyRequest()
                .permitAll()
        }
        .build()

    @Bean
    fun authentication(): MapReactiveUserDetailsService {
        val user = User.
            withDefaultPasswordEncoder()
            .username("user")
            .password("password")
            .roles("TEST")
            .build()
        return MapReactiveUserDetailsService(user)
    }

    /** The key payload resolver, as production registers it. See CHAT-avduuqwp, D1. */
    @Bean
    fun verifiedKeyResolverCustomizer(verifiers: ObjectProvider<KeyVerifier<*>>): RSocketMessageHandlerCustomizer =
        RSocketMessageHandlerCustomizer { handler ->
            handler.argumentResolverConfigurer.addCustomResolver(VerifiedKeyArgumentResolver(handler.decoders, verifiers))
        }

    /**
     * A verifier that holds every id under the fixed test root. Other test
     * contexts scan this package and hold no roots, so the roots are optional.
     */
    @Bean
    fun testKeyVerifier(rootKeys: ObjectProvider<RootKeys<Long>>): KeyVerifier<Long> =
        TestVerifiers.acceptingTestRoot(rootKeys.getIfAvailable { RootKeys() })

    /**
     * The server decodes a Key payload, so it needs the domain codec, as
     * production has. Without it every Key request failed with a type
     * definition error, and a test that expected an error passed on it.
     */
    @Bean
    fun rSocketStrategiesCustomizer(): RSocketStrategiesCustomizer {
        val mapper = JsonMapper.builder()
            .addModule(ChatJackson3Modules().chatJackson3Module())
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
}