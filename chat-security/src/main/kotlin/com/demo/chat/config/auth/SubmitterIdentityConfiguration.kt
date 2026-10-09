package com.demo.chat.config.auth

import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.security.access.ContextSubmitterIdentity
import com.demo.chat.service.command.SubmitterIdentity
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"], havingValue = "true")
open class SubmitterIdentityConfiguration<T>(private val rootKeys: RootKeys<T>) {
    @Bean
    open fun submitterIdentity(): SubmitterIdentity<T> = ContextSubmitterIdentity(ContextIdentity(rootKeys), rootKeys)
}
