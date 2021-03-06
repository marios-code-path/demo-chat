package com.demo.chat.deploy.config.core

import com.demo.chat.codec.JsonNodeAnyDecoder
import com.demo.chat.domain.serializers.JacksonModules
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class SerializationConfiguration : JacksonConfiguration()

open class JacksonConfiguration {
    @Bean
    open fun jacksonModules() = JacksonModules(JsonNodeAnyDecoder, JsonNodeAnyDecoder)

    @Bean
    open fun objectMapper(modules: JacksonModules): ObjectMapper =
            jacksonObjectMapper().registerModule(KotlinModule()).apply {
                propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
                with(modules) {
                    registerModules(
                            messageModule(),
                            keyModule(),
                            topicModule(),
                            membershipModule(),
                            userModule())
                }
                findAndRegisterModules()
            }!!
}