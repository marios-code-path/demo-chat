package com.demo.chat

import com.demo.chat.config.CassandraProperties
import com.demo.chat.config.ConfigurationRedisTopics
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType

@SpringBootApplication
@ComponentScan(excludeFilters = [
    ComponentScan.Filter(type = FilterType.ANNOTATION, value = [ExcludeFromTests::class])
])
class ChatServiceRsocketApplication

@EnableConfigurationProperties(CassandraProperties::class, ConfigurationRedisTopics::class)
@ExcludeFromTests
@Configuration
class ServiceDiscoveryConfiguration {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<ChatServiceRsocketApplication>(*args)
        }
    }
}

annotation class ExcludeFromTests