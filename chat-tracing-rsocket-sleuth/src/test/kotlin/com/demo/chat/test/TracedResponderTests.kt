package com.demo.chat.test

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(
    properties = [
        "app.service.core.key=long", "server.port=0", "management.endpoints.enabled-by-default=false",
        "app.client.rsocket.core.persistence", "spring.rsocket.server.port=7890",
        "spring.cloud.service-registry.auto-registration.enabled=false","app.rsocket.client.requester.factory=test",
        "spring.shell.interactive.enabled=false"]
)
class TracedResponderTests {

}