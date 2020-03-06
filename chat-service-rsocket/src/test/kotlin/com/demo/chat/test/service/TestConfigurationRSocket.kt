package com.demo.chat.test.service

import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.serializers.JacksonModules
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleAbstractTypeResolver
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler


class TestModules : JacksonModules(JsonNodeAnyCodec, JsonNodeAnyCodec)

@Import(TestModules::class,
        JacksonAutoConfiguration::class,
        RSocketStrategiesAutoConfiguration::class)
class TestConfigurationRSocket {

    @ConditionalOnMissingBean
    @Bean
    fun serverMessageHandler(strategies: RSocketStrategies): RSocketMessageHandler {
        val handler = RSocketMessageHandler()
        handler.rSocketStrategies = strategies
        handler.afterPropertiesSet()
        return handler
    }
}