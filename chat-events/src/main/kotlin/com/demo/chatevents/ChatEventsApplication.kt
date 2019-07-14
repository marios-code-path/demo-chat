package com.demo.chatevents

import com.demo.chatevents.service.TopicServiceMemory
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SpringBootConfiguration
class ChatEventsApplication

fun main(args: Array<String>) {
    runApplication<ChatEventsApplication>(*args)
}