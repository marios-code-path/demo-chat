package com.demo.chat.service

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.router
import java.util.*

@Configuration
class AppRouters(val service: ChatService) {

    data class UserResponse(val token: UUID, val handle: String, val timestamp: Date)

    @Bean
    fun routes(): RouterFunction<ServerResponse> = router {
        POST("/newuser") { req ->
            ServerResponse
                    .ok()
                    .body(service
                            .newUser(req.queryParam("handle").orElseThrow { Exception("User Handle Expected.") },
                                    req.queryParam("name").orElse("Horus"))
                            .map {
                                UserResponse(it.id, it.handle, it.timestamp)
                            }
                    )
        }

    }
}

