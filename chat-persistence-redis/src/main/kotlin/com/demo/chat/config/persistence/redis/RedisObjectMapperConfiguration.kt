package com.demo.chat.config.persistence.redis

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.serializers.JacksonModules
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers the chat domain Jackson modules (key, user, topic, message,
 * membership, authMetadata, keyDataPair) on the auto-configured
 * [com.fasterxml.jackson.databind.ObjectMapper] so the Redis persistence
 * beans can deserialize domain entities in a Spring Boot deploy context.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"], havingValue = "redis")
class RedisObjectMapperConfiguration {

    @Bean
    fun chatDomainJacksonCustomizer(): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { builder ->
            val modules = JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)
            builder.modulesToInstall(*modules.allModules().toTypedArray())
        }
}