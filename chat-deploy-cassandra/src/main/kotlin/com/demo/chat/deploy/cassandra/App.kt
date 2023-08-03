package com.demo.chat.deploy.cassandra

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories
import org.springframework.web.reactive.config.EnableWebFlux

@SpringBootApplication(
    scanBasePackages = ["com.demo.chat.config"],
    proxyBeanMethods = false
)
@EnableReactiveCassandraRepositories(
    basePackages = [
        "com.demo.chat.persistence.cassandra.repository",
        "com.demo.chat.index.cassandra.repository"
    ]
)
@EnableWebFlux
class App {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }
}