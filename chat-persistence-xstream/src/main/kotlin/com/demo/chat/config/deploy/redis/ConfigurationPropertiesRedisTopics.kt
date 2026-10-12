package com.demo.chat.config.deploy.redis

import com.demo.chat.config.ConfigurationPropertiesRedis
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Redis connection properties for the Redis deploy.
 *
 * Constructor binding is automatic in Spring Boot 3.x (the sole non-default
 * constructor is used); an explicit @ConstructorBinding must NOT be added —
 * Kotlin copies it onto the synthetic no-args constructor generated for the
 * default arguments, and the binder rejects that with
 * "declares @ConstructorBinding on a no-args constructor".
 */
@ConfigurationProperties("redis-topics")
data class ConfigurationPropertiesRedisTopics(
    override val host: String = "127.0.0.1",
    override val port: Int = 6379
) : ConfigurationPropertiesRedis