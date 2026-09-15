package com.demo.chat.deploy.test.security

import com.demo.chat.config.WebFluxSecurity
import com.demo.chat.config.deploy.security.ActuatorWebSecurityConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * The two chains, and one route that belongs to neither actuator nor chat.
 *
 * The class sits in its own package, so its component scan reaches these test
 * classes alone.
 */
@SpringBootApplication(proxyBeanMethods = false)
@Import(ActuatorWebSecurityConfiguration::class, ApplicationChainConfiguration::class)
class BothChainsApplication {

    /**
     * ActuatorWebSecurityConfiguration takes this bean.
     *
     * The value matches PasswordEncoderConfiguration in chat-security, which
     * is what every deployment builds. A bare BCryptPasswordEncoder writes a
     * hash with no prefix, and the authentication manager then reads it with
     * the delegating encoder and rejects every password.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder =
        PasswordEncoderFactories.createDelegatingPasswordEncoder()
}

/**
 * The application chain of chat-webflux, and nothing else of that class.
 *
 * This configuration calls the production method, so the policy under test is
 * the production policy. A change to WebFluxSecurity.filterChain reaches this
 * test.
 *
 * An @Import of WebFluxSecurity itself would also bring its
 * @ComponentScan("com.demo.chat.controller.webflux"). Several controllers
 * there carry no condition, and each one needs service beans that a security
 * test has no reason to build. Those beans decide nothing about which chain
 * owns which route.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class ApplicationChainConfiguration {

    @Bean
    fun applicationFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain? =
        WebFluxSecurity().filterChain(http)
}

/**
 * One application route.
 *
 * A real chat controller carries a ConditionalOnProperty and needs services
 * that this context does not build. This route needs nothing, and the question
 * under test is which chain owns it.
 */
@RestController
class OpenTestController {

    @GetMapping("/test/open", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun open(): Mono<String> = Mono.just("open")
}
