package com.demo.chat.config.deploy.cassandra

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.service.composite.CompositeServiceBeansConfiguration
import com.demo.chat.config.service.composite.access.CompositeServiceAccessBeansConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.service.core.*
import com.demo.chat.service.security.AccessBroker
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.ReactiveSecurityContextHolder

@Configuration
@ConditionalOnProperty("app.service.composite")
class CompositeServiceConfiguration {

    @Bean
    @ConditionalOnProperty("app.service.composite.security")
    fun <T> springSecurityCompositeServiceAccessBeans(
        accessBroker: AccessBroker<T>,
        rootKeys: RootKeys<T>,
        compositeServiceBeansConfiguration: CompositeServiceBeansConfiguration<T, String, IndexSearchRequest>
    ): CompositeServiceBeans<T, String> = CompositeServiceAccessBeansConfiguration(
        accessBroker = accessBroker,
        principalKeyPublisher = {
            ReactiveSecurityContextHolder.getContext()
                .map { it.authentication.principal as ChatUserDetails<T> }
                .map { it.user.key }
        },
        rootKeys = rootKeys,
        compositeServiceBeansConfiguration = compositeServiceBeansConfiguration
    )
}