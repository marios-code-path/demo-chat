package com.demo.chat

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.IndexSearchRequestConverters
import com.demo.chat.domain.RequestToQueryConverters
import com.demo.chat.domain.TypeUtil
import com.demo.chat.security.service.CoreUserDetailsService
import com.demo.chat.service.client.ClientDiscovery
import com.demo.chat.service.client.discovery.LocalhostDiscovery
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [ChatApp::class, TestConfig::class, RequiredAppBeans::class],
    properties = [
        "spring.config.location=classpath:application.yml",
        "app.key.type=long",
        "app.client.protocol=rsocket",
        "app.primary=authserv_test",
        "app.rsocket.transport.unprotected",
        "app.client.rsocket.composite.user",
        "app.client.rsocket.composite.message",
        "app.client.rsocket.composite.topic",
        "app.client.rsocket.core.persistence",
        "app.client.rsocket.core.index",
        "app.service.composite.auth",
        "app.rsocket.transport.security.type=unprotected"
    ]
)
@ActiveProfiles("memory")
class AuthorizationServerDeployTests {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun signingKey(registry: DynamicPropertyRegistry) {
            AuthorizationServerTestSigningKey.register(registry)
        }
    }

    @Test
    fun contextLoads() {
    }
}

/**
 * The beans this test supplies, and the one it must not.
 *
 * **This class declared `requestToQueryConverters` until 2026-09-21, and no
 * main source did.** So `contextLoads` passed while the deployment could not
 * start at all. `AuthServerAppConfiguration` owns that bean now, and this
 * test reads it from main source. Removing it from main makes this test fail,
 * which is the point. See CHAT-nfvielrm.
 *
 * `localDiscovery` stays, and it hides nothing. A deployment sets
 * `app.client.discovery=properties`, and `ClientDiscoveryConfiguration` in
 * `chat-client-rsocket` then supplies `PropertiesBasedDiscovery`. This test
 * sets no discovery property, so it states one here instead.
 *
 * **Do not add a production bean to this class.** A test that supplies what a
 * deployment lacks reports nothing. That is the defect this comment records,
 * and `CHAT-etfnihnu` recorded the same shape for the embedding model.
 */
@TestConfiguration
class TestConfig {
    @Bean
    fun localDiscovery(): ClientDiscovery = LocalhostDiscovery("127.0.0.1", 9000)
}

/** States the application beans that both authorization-server tests need. */
@TestConfiguration
class RequiredAppBeans(
    private val typeUtil: TypeUtil<Long>,
    private val coreUserDetailsService: CoreUserDetailsService<Long>
)
