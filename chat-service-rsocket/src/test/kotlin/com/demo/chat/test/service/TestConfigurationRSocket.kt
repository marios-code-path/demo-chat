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
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler

@Import(TestModules::class, SerializationConfiguration::class, JacksonAutoConfiguration::class, RSocketStrategiesAutoConfiguration::class)
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

class TestModules() : JacksonModules(JsonNodeAnyCodec, JsonNodeAnyCodec)

class SerializationConfiguration {

    @Bean
    fun mapper(): ObjectMapper =
            ObjectMapper().apply {
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
                registerModules(KotlinModule())
                findAndRegisterModules()
                setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.NONE)
                setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
                setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)
                setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
            }

    // Register this abstract module to let the app know when it sees a Interface type, which
    // concrete type to use on the way out.
    fun <T> module(name: String, iface: Class<T>, concrete: Class<out T>) = SimpleModule("CustomModel$name", Version.unknownVersion())
            .apply { setAbstractTypes(SimpleAbstractTypeResolver().apply { addMapping(iface, concrete) }) }

}