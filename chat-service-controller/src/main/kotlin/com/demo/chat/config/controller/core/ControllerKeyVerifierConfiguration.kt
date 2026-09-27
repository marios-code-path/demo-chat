package com.demo.chat.config.controller.core

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.KeyVerifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The verifier of the RSocket controllers. A controller verifies each key or
 * id of its input through it. See `CHAT-avduuqwp`, T4.
 *
 * The webflux module declares the same bean under the same condition. A
 * deployment that exposes both transports gets one verifier.
 */
@Configuration
class ControllerKeyVerifierConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun <T> keyVerifier(keyBeans: KeyServiceBeans<T>, rootKeys: RootKeys<T>): KeyVerifier<T> =
        KeyVerifier(keyBeans.keyService(), rootKeys)
}
