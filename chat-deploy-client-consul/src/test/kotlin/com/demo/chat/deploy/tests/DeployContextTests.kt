package com.demo.chat.deploy.tests

import com.demo.chat.deploy.client.consul.config.ConsulClientAppConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [ConsulClientAppConfiguration::class]
)
@TestPropertySource(
    properties = [
        "app.service.core.key=long", "spring.cloud.consul.config.enabled=false",
        "app.client=rsocket", "server.port=0", "management.endpoints.enabled-by-default=false",
        "app.rsocket.transport.insecure",
        "app.client.rsocket.core.persistence", "app.client.rsocket.core.index", "app.client.rsocket.core.pubsub",
        "app.client.rsocket.core.secrets", "app.client.rsocket.core.key",
        "spring.cloud.service-registry.auto-registration.enabled=false","app.rsocket.client.requester.factory=test",
        "spring.shell.interactive.enabled=false"]
)
class DeployContextTests {

    @Test
    fun contextLoads() {}
}