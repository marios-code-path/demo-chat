package com.demo.chat.test.service

import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.serializers.JacksonModules
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler

@Import(JacksonAutoConfiguration::class, RSocketStrategiesAutoConfiguration::class, SerializationConfiguration::class)
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

class SerializationConfiguration {
    @Bean
    fun mapper(): ObjectMapper {
        return ObjectMapper().apply {
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
            val modules = JacksonModules(JsonNodeAnyCodec, JsonNodeAnyCodec)
            registerModules(
                    modules.userModule(),
                    modules.keyModule()
            )
            setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.NONE)
            setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
            setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)
            setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
        }
    }
}