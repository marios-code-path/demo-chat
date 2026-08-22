package com.demo.chat.config.deploy.redis

import com.demo.chat.config.persistence.redis.RedisKeyServices
import com.demo.chat.config.persistence.redis.RedisPersistenceServices
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Deploy-time activation of the Redis persistence stack.
 *
 * The imported configs are themselves @ConditionalOnProperty; this class
 * makes the wiring explicit for the redis deployment and activates when
 * `app.service.core.persistence=redis` (set in application-redis.yml).
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"], havingValue = "redis")
@Import(RedisKeyServices::class, RedisPersistenceServices::class)
class RedisPersistenceConfiguration