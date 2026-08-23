package com.demo.chat

import com.demo.chat.service.client.discovery.LocalhostDiscovery
import com.demo.chat.domain.TypeUtil
import com.demo.chat.security.service.CoreUserDetailsService
import com.demo.chat.service.client.ClientDiscovery
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cloud.client.ServiceInstance
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [ChatApp::class, TestConfig::class],
    properties = [
        "spring.config.location=classpath:application.yml",
        "app.key.type=long", "app.client.protocol=rsocket", "app.primary=authserv_test",
        "app.rsocket.transport.unprotected", "app.client.rsocket.composite.user",
        "app.client.rsocket.composite.message", "app.client.rsocket.composite.topic",
        "app.client.rsocket.core.persistence",
        "app.client.rsocket.core.index", "app.service.composite.auth",
    "app.rsocket.transport.security.type=unprotected"

    ]
)
@ActiveProfiles("memory")
//@Disabled
class AuthorizationServerDeployTests {

    companion object {
        /**
         * The signing key is generated per run rather than committed.
         *
         * AuthorizationServerConfig reads it from `app.oauth2.jwk.path`, and the
         * repository has no `server_keycert.jwk` on the test classpath —
         * `gen-dckeys.sh` produces one and copies it here, but nothing in the
         * build runs that script, so the context failed to start on any machine
         * where it had not been run by hand.
         *
         * EC P-256, because jwtCustomizer signs with ES256. No x5c chain: the
         * production key carries one, but nothing in this context verifies it.
         */
        private val jwkFile: Path = generateSigningKey()

        private fun generateSigningKey(): Path {
            val jwk = ECKeyGenerator(Curve.P_256)
                .keyID(UUID.randomUUID().toString())
                .generate()

            val file = Files.createTempFile("authserver-test-signing-key", ".jwk")
            file.toFile().deleteOnExit()
            Files.writeString(file, jwk.toJSONString())
            return file
        }

        @JvmStatic
        @DynamicPropertySource
        fun signingKey(registry: DynamicPropertyRegistry) {
            registry.add("app.oauth2.jwk.path") { "file:" + jwkFile.toAbsolutePath() }
        }
    }

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