package com.demo.chat.gateway.routers

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.cloud.gateway.route.builder.routes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class GlobalRouters {

    @Value("\${app.rest.port:6792}")
    private lateinit var webApiPort: String

    // http://host:port/persist/user/get/12345
    // to
    // GET http://host:port/user/12345
    // PUT http://host:port/user {body}
    // DELETE http://host:port/user/12345
    //
    @Bean
    fun wildcardRoutes(builder: RouteLocatorBuilder) = builder.routes {
        route(id = "global") {
            path("/**")
            uri("http://localhost:$webApiPort")
        }

    }
}