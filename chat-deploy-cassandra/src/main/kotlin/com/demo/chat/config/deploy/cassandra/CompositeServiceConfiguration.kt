package com.demo.chat.config.deploy.cassandra

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.service.composite.CompositeServiceBeansConfiguration
import com.demo.chat.config.service.composite.access.CompositeServiceAccessBeansConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.security.AccessBroker
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.publisher.Mono

@Configuration
class CompositeServiceConfiguration {

    @Bean
    @ConditionalOnProperty("app.service.composite.security")
    fun <T : Any> serviceAccessCompositeServiceAccessBeans(
        accessBroker: AccessBroker<T>,
        rootKeys: RootKeys<T>,
        compositeServiceBeansConfiguration: CompositeServiceBeansConfiguration<T, String, IndexSearchRequest>
    ): CompositeServiceBeans<T, String> = CompositeServiceAccessBeansConfiguration(
        accessBroker = accessBroker,
        principalKeyPublisher = {
            ReactiveSecurityContextHolder.getContext()
                // **A null authentication is anonymous**, as in
                // SpringSecurityAccessBrokerService. The owner decided that on
                // 2026-09-18. This seam carries no switchIfEmpty of its own,
                // so it names the Anon root key directly. Without that, a null
                // authentication would answer an empty publisher, and an empty
                // principal is not the same as an anonymous one.
                .mapNotNull { it.authentication?.principal as ChatUserDetails<T>? }
                .map { it.user.key }
                .switchIfEmpty(Mono.just(rootKeys.getRootKey(Anon::class.java)))
        },
        rootKeys = rootKeys,
        compositeServiceBeansConfiguration = compositeServiceBeansConfiguration
    )
}