package com.demo.chat.edge

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ChatHttpApplication

fun main(args: Array<String>) {
	runApplication<ChatHttpApplication>(*args)
}
