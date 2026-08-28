package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimGuard
import com.demo.chat.domain.NodeIdClaimStore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource

/**
 * Memory offers no claim, and it must not look as though it does.
 *
 * The limit of this test, stated so it is not overclaimed: memory still
 * requires `app.nodeid`. Stage 1 requires it wherever a key generator
 * activates. What this test pins is that the claim design adds no
 * requirement to memory and supplies no no-operation store.
 *
 * A no-operation store that returned Granted would be false safety. A
 * memory store is per process, so it can prove nothing about another
 * deployment.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [ChatApp::class]
)
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment", "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=memory", "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.service.security.userdetails"
    ]
)
class MemoryClaimAbsenceTests {

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `a memory deployment registers no claim store`() {
        Assertions.assertTrue(context.getBeansOfType(NodeIdClaimStore::class.java).isEmpty())
    }

    @Test
    fun `a memory deployment registers no claim guard`() {
        Assertions.assertTrue(context.getBeansOfType(NodeIdClaimGuard::class.java).isEmpty())
    }

    @Test
    fun `a memory deployment still supplies the node id from stage one`() {
        Assertions.assertEquals(1, context.getBean(NodeId::class.java).value)
    }
}
