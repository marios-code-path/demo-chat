package com.demo.chat

import org.springframework.boot.runApplication

class ChatGatewayApp {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<ChatGatewayApp>(*args)
        }
    }
}