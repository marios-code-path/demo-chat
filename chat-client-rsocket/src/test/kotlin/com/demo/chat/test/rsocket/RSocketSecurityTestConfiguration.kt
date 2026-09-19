package com.demo.chat.test.rsocket

import com.demo.chat.config.ChatJackson3Modules
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import tools.jackson.databind.json.JsonMapper
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

    /**
     * The server RSocket strategies, including the chat domain Jackson 3 codec.
     *
     * **The server decodes the request payload, and it needs the domain
     * module for the same reason the client does.** Without it a Key in a
     * request answers an RSocket application error 0x201 with a type
     * definition error, which 15 tests demonstrated.
     *
     * The decoder is added **before** the default one. Spring picks the first
     * decoder that matches the type, so an appended decoder never runs.
     *
     * **The CBOR path is not wired here.** TargetIdentifierInterceptor
     * resolves its decoder for MediaType.APPLICATION_CBOR, no test reaches
     * that path, and it gets its own reading before it gets a change.
     * See CHAT-qwmjrixq.
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