package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.ChatException
import com.demo.chat.service.core.StoreShapeCheck
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.time.Duration

/**
 * The Redis store shape. The key registry is one hash for each key type,
 * `chat:keys:<keyType>`. A hash named `chat:keys` has no key type segment, so
 * an earlier release wrote it. See `CHAT-avduuqwp`, T7.
 */
class RedisStoreShapeCheck(private val stringTemplate: ReactiveStringRedisTemplate) : StoreShapeCheck {

    override fun check() {
        if (stringTemplate.hasKey(LEGACY_REGISTRY).block(Duration.ofSeconds(10)) == true) throw ChatException(
            "Redis holds the key registry hash $LEGACY_REGISTRY without a key type segment. " +
                "Recreate the store: delete the chat:* keys and start again. This release has no migration."
        )
    }

    companion object {
        const val LEGACY_REGISTRY = "chat:keys"
    }
}
