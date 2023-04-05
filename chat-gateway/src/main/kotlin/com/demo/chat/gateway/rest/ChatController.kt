package com.demo.chat.gateway.rest

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@RestController("/chat")
class ChatController {


    @GetMapping("/users")
    fun getUsers(): Flux<String> {

        return Flux.just("user1", "user2")
    }

}