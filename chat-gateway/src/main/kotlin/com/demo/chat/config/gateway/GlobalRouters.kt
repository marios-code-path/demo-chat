package com.demo.chat.config.gateway

import com.demo.chat.service.client.ClientDiscovery
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.cloud.gateway.route.builder.routes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class GlobalRouters() {

    // http://host:port/persist/user/get/12345
    // to
    // GET http://host:port/user/12345
    // PUT http://host:port/user {body}
    // DELETE http://host:port/user/12345
    //
//    @Bean
//    fun wildcardRoutes(builder: RouteLocatorBuilder) = builder.routes {
//        clientDiscovery.getServiceInstance("core-service-http")
//            .map {instance ->
//                route(id = "global") {
//                    path("/**")
//                    uri("${instance.scheme}://core-service-http:${instance.port}")
//                }
//            }
//            .subscribe()
//    }
}