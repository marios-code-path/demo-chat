package com.demo.chat.config.presence

import com.demo.chat.presence.memory.InMemoryPresenceService
import com.demo.chat.service.core.PresenceService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the in-memory presence service when app.service.presence.backend=memory.
 * Production would use Redis TTL keys for heartbeat expiry and pub/sub for presence fan-out.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.presence", name = ["backend"], havingValue = "memory", matchIfMissing = false)
class PresenceServiceBeans<T> {

    @Bean
    fun presenceService(): PresenceService<T> =
        InMemoryPresenceService()
}
