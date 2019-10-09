package com.demo.chat

import com.demo.chat.config.SampleProps
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType

@SpringBootApplication
@ComponentScan(excludeFilters = [
    ComponentScan.Filter(type = FilterType.ANNOTATION, value = [ExcludeFromTests::class])
])
class ChatServiceRsocketApplication

@EnableConfigurationProperties
@ExcludeFromTests
@Configuration
class ServiceDiscoveryConfiguration {
    private val log = LoggerFactory.getLogger(this::class.qualifiedName)
    fun main(args: Array<String>) {
        runApplication<ChatServiceRsocketApplication>(*args)
    }

    @Bean
    fun appRun(sampleProp: SampleProps): ApplicationRunner =
            ApplicationRunner {args ->
                log.info("SampleProp: ${sampleProp.name}")
            }
}
annotation class ExcludeFromTests