package com.demo.chat.test.rsocket.controller.core

import com.demo.chat.convert.JsonNodeToAnyEncoder
import com.demo.chat.domain.serializers.JacksonModules
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler


class TestModules : JacksonModules(JsonNodeToAnyEncoder, JsonNodeToAnyEncoder)

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