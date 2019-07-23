package com.demo.chat

import org.springframework.boot.autoconfigure.EnableAutoConfiguration
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

@EnableConfigurationProperties
@EnableAutoConfiguration
@Configuration
@ExcludeFromTests
class ServiceDiscoveryConfiguration

fun main(args: Array<String>) {
    runApplication<ChatServiceRsocketApplication>(*args)
}

annotation class ExcludeFromTests