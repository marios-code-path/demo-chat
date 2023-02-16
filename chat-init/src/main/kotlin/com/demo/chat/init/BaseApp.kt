package com.demo.chat.init

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.demo.chat.config"])
class BaseApp {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<InitApp>(*args)
        }
    }
}