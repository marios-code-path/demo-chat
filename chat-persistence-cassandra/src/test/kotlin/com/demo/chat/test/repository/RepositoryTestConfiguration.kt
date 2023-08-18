package com.demo.chat.test.repository

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.User
import com.demo.chat.domain.serializers.UserDeserializer
import com.demo.chat.test.CassandraTestContainerConfiguration
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories


@Configuration
@EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.persistence.cassandra.repository"])
@Import(TestObjectMapperConfiguration::class)
class RepositoryTestConfiguration(props: CassandraProperties) : CassandraTestContainerConfiguration(props)


open class TestObjectMapperConfiguration {
    @Bean
    fun objectMapper() = ObjectMapper().registerModule(KotlinModule.Builder().build()).apply {
        propertyNamingStrategy = PropertyNamingStrategies.LOWER_CAMEL_CASE
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
        findAndRegisterModules()
        registerModules(DefaultChatJacksonModules().allModules())
    }!!
}