package com.demo.chat

import com.demo.chat.client.discovery.LocalhostDiscovery
import com.demo.chat.domain.TypeUtil
import com.demo.chat.secure.service.CoreUserDetailsService
import com.demo.chat.service.client.ClientDiscovery
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cloud.client.ServiceInstance
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono
import java.util.*

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [ChatApp::class, TestConfig::class],
    properties = [
        "spring.config.location=classpath:application.yml",
        "app.key.type=long", "app.client.protocol=rsocket", "app.primary=authserv_test",
        "app.rsocket.transport.unprotected", "app.client.rsocket.composite.user",
        "app.client.rsocket.composite.message", "app.client.rsocket.composite.topic",
        "app.service.security.userdetails", "app.client.rsocket.core.persistence",
        "app.client.rsocket.core.index", "app.service.composite.auth",
        "app.oauth2.jwk.path=file:/Users/grayma/workspace/demo-chat/encrypt-keys/server_keycert.jwk",
    "app.rsocket.transport.security.type=unprotected"

    ]
)
@ActiveProfiles("memory")
//@Disabled
class AuthorizationServerDeployTests {

    @Autowired
    private lateinit var typeUtil: TypeUtil<Long>

    @Autowired
    private lateinit var coreUserDetailsService: CoreUserDetailsService<Long>

    @Test
    fun contextLoads() {
    }
}

@TestConfiguration
class TestConfig {
    @Bean
    fun localDiscovery(): ClientDiscovery = LocalhostDiscovery("127.0.0.1", 9000)
    fun discovery() = object : ClientDiscovery {

        override fun getServiceInstance(serviceName: String): Mono<ServiceInstance> {
            TODO("Not yet implemented")
        }
    }
}