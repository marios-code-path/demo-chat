package com.demo.chat.chatconsumers

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ChatConsumersApplication

fun main(args: Array<String>) {
	runApplication<ChatConsumersApplication>(*args)
}
