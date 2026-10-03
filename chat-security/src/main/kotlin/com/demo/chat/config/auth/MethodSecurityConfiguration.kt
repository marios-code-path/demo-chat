package com.demo.chat.config.auth

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.security.AccessBroker
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity

@Configuration
@EnableReactiveMethodSecurity
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"], havingValue = "true")
class MethodSecurityConfiguration {

    @Bean
    fun <T> chatAccess(
        access: AccessBroker<T>,
        rootKeys: RootKeys<T>,
        keyBeans: KeyServiceBeans<T>,
    ): SpringSecurityAccessBrokerService<T> =
        SpringSecurityAccessBrokerService(access, rootKeys, KeyVerifier(keyBeans.keyService(), rootKeys))
}
