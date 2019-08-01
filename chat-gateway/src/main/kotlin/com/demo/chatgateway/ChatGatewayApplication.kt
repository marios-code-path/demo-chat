package com.demo.chatgateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ChatGatewayApplication

fun main(args: Array<String>) {
	runApplication<ChatGatewayApplication>(*args)
}
