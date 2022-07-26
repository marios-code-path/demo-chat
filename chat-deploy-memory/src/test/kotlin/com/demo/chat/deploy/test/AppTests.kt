package com.demo.chat.deploy.test

import com.demo.chat.deploy.memory.App
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

//TODO Fixme!!! Make this tests work
@SpringBootTest(classes = [App::class])
@TestPropertySource(
    properties = [
        "app.proto=rsocket", "spring.cloud.consul.config.enabled=false",
        "app.primary=core", "server.port=0", "management.endpoints.enabled-by-default=false",
        "spring.shell.interactive.enabled=false", "app.service.core.key=long"]
)
class AppTests {

    @Test
    fun contextLoads() {
    }
}