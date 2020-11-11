package com.demo.chat.deploy.config

import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.serializers.JacksonModules
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class AppJacksonModules : JacksonModules(JsonNodeAnyCodec, JsonNodeAnyCodec)

@Configuration
class SerializationConfiguration : JacksonConfiguration()

open class JacksonConfiguration {
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