package com.demo.chat.deploy.cassandra

import com.demo.chat.deploy.AppConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication(scanBasePackages = ["com.demo.chat.deploy.cassandra"])
@Import(AppConfiguration::class)
class App {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }
}