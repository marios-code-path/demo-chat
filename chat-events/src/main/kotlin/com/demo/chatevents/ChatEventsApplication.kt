package com.demo.chatevents

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SpringBootApplication
class ChatEventsApplication

fun main(args: Array<String>) {
    runApplication<ChatEventsApplication>(*args)
}


@Configuration
class ChatEventsConfiguration {
    @Bean
    fun topicServiceInMemory(): ChatTopicInMemoryService =
            ChatTopicInMemoryService()
}