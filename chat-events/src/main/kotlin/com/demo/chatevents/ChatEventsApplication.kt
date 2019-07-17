package com.demo.chatevents

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.runApplication

@SpringBootConfiguration
class ChatEventsApplication

fun main(args: Array<String>) {
    runApplication<ChatEventsApplication>(*args)
}