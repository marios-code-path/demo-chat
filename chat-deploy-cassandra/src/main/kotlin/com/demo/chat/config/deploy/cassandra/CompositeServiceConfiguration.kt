package com.demo.chat.config.deploy.cassandra

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.service.composite.CompositeServiceBeansConfiguration
import com.demo.chat.config.service.composite.access.CompositeServiceAccessBeansConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.service.security.AccessBroker
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
        // **`ContextIdentity` holds the rule.** This seam carried its own
        // copy, and it named the Anon root key for a context with no
        // authentication. An empty answer means denied now.
        // See `docs/IDENTITY-POLICY.md`.
        principalKeyPublisher = { ContextIdentity(rootKeys).identity() },
        rootKeys = rootKeys,
        compositeServiceBeansConfiguration = compositeServiceBeansConfiguration
    )
}