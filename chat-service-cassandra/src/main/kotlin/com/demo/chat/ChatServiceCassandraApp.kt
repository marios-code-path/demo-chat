package com.demo.chat

import com.demo.chat.config.CassandraConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@Import(CassandraConfiguration::class)
class ChatServiceCassandraApp

fun main(args: Array<String>) {
    runApplication<ChatServiceCassandraApp>(*args)
}