package com.demo.chat.streams.gateway

import com.demo.chat.domain.User
import com.demo.chat.service.EnricherPersistenceStore
import com.demo.chat.service.conflate.KeyEnricherPersistenceStore
import com.demo.chat.streams.functions.UserCreateRequest
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.integration.config.EnableIntegration
import org.springframework.web.reactive.config.EnableWebFlux
import java.util.function.Function

@SpringBootApplication
@EnableIntegration
@EnableWebFlux
class App {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }

    class UserFlows<T: Long>(
        store: KeyEnricherPersistenceStore<T, UserCreateRequest, User<T>>
    ) : EnricherStoreFlows<T, UserCreateRequest, User<T>>(
        store,
        Function { u -> u.key },
        "users"
    )

}