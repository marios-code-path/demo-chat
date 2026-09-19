package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.config.Jackson2MapperConfiguration
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.reactive.result.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer

/**
 * The permit-all chain that the controller slice tests run behind.
 *
 * **This fixture does not test security. It removes security from the way.**
 * The slice tests here verify controller behaviour, request binding and wire
 * shapes, and a chain that refused anything would only obscure that.
 *
 * ### Why the chain is built rather than injected
 *
 * It injected `ServerHttpSecurity` until 2026-09-18. **No `@WebFluxTest` slice
 * supplies that bean under Spring Boot 4**, so 12 test classes failed to load
 * a context and 174 tests errored. The bean is not gone: three main sources
 * still inject it, and the packaged launch gate proves it exists in a full
 * application context, because the actuator answers 401 through a chain that
 * takes it.
 *
 * So the repair is narrow. Spring Security 7 adds the static factory
 * `ServerHttpSecurity.http()`, and this fixture uses it. Importing the
 * production `WebFluxSecurity` instead would pull its component scan into
 * every slice and add unrelated controller dependencies, which makes a larger
 * and less stable test context.
 *
 * ### Where production security is actually covered
 *
 * **A chain built from the static factory is not the instance the container
 * would configure**, so this file proves nothing about production security.
 * Three layers cover that, and each one owns a different claim.
 *
 * | Layer | What it proves |
 * |---|---|
 * | These slice tests | Controller behaviour, behind permit-all |
 * | The packaged launch gate | The production `WebFluxSecurity.filterChain` |
 * | `SecurityChainOwnershipTests` in chat-deploy | Which chain owns which route |
 *
 * See CHAT-wvhxddgy.
 */
@TestConfiguration
// Jackson2MapperConfiguration supplies the named Jackson 2 mapper. Spring
// Boot 4 builds Jackson 3 mappers only, and the controllers under test take
// the Jackson 2 one by qualifier. A slice that imports the modules without
// the mapper leaves that qualifier unsatisfied. See CHAT-sxydbspq.
@Import(
    DefaultChatJacksonModules::class,
    Jackson2MapperConfiguration::class,
    ChatJackson3Modules::class,
)
class WebFluxTestConfiguration : WebFluxConfigurer {

    /**
     * The `@AuthenticationPrincipal` resolver, which the slice does not get.
     *
     * **Production registers it through `@EnableWebFluxSecurity`** on
     * `WebFluxSecurity`, and this fixture deliberately does not enable that.
     * Without the resolver, WebFlux falls back to model attribute binding for
     * a `ChatUserDetails` parameter, tries to construct one from request
     * parameters, and a Kotlin non-null constructor then fails. The caller
     * sees 400 and the message names `ChatUserDetails.<init>`.
     *
     * Seven tests failed that way. The controllers declare the parameter
     * nullable already, so the defect was the missing resolver rather than
     * the signature.
     *
     * `RSocketServerConfiguration` in chat-service-controller adds the RSocket
     * equivalent the same way, so this follows a pattern the repository
     * already uses.
     */
    override fun configureArgumentResolvers(configurer: ArgumentResolverConfigurer) {
        configurer.addCustomResolver(
            AuthenticationPrincipalArgumentResolver(ReactiveAdapterRegistry.getSharedInstance())
        )
    }


    @Bean
    fun filterChain(): SecurityWebFilterChain? = ServerHttpSecurity.http()
        .authorizeExchange {

            it.anyExchange()
                .permitAll()
        }
        .cors { it.disable() }
        .csrf { it.disable() }
        .build()
}
