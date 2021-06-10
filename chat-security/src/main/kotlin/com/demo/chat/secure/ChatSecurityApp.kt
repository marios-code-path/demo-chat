package com.demo.chat.secure

import org.springframework.boot.runApplication


class ChatSecurityApp {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<ChatSecurityApp>(*args)
        }
    }
}