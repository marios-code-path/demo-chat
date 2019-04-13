package com.demo.chat

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


@SpringBootApplication
class ChatServiceApplication

fun main(args: Array<String>) {
    runApplication<ChatServiceApplication>(*args)
}

