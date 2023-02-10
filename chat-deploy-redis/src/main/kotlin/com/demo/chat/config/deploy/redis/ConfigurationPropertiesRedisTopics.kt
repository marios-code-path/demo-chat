package com.demo.chat.config.deploy.redis

import com.demo.chat.config.ConfigurationPropertiesRedis
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties("redis-topics")
data class ConfigurationPropertiesRedisTopics
@ConstructorBinding constructor(
    override val host: String = "127.0.0.1",
    override val port: Int = 6379
) : ConfigurationPropertiesRedis