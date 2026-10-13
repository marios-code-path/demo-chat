package com.demo.chat.config.auth

import com.demo.chat.security.access.ContextPointerTargetAccess
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.command.PointerTargetAccess
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"], havingValue = "true")
open class PointerTargetAccessConfiguration<T> {
    @Bean
    open fun pointerTargetAccess(chatAccess: SpringSecurityAccessBrokerService<T>): PointerTargetAccess<T> =
        ContextPointerTargetAccess(chatAccess)
}
