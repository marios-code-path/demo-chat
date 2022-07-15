package com.demo.chat.deploy.cassandra

import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories

@SpringBootApplication(excludeName = ["com.demo.chat.deploy"])
@Import(
    DefaultChatJacksonModules::class,
)
class App {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }
}