package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.service.security.AuthenticationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

/**
 * The `ByIdRequest` routes on a `Long` deployment that no other route test
 * reads, called as Admin. `UuidCompositeRouteTests` reads the same routes on a
 * `UUID` deployment. See `CHAT-oeyxthsy`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-long-id-routes",
        "app.server.proto=rsocket", "server.port=0", "spring.rsocket.server.port=0",
        "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory", "app.service.core.pubsub=memory",
        "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory",
        "app.service.composite", "app.command.bus=memory", "app.service.composite.auth=true",
        "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic",
        "app.controller.pubsub",
        "app.service.security.userdetails", "app.users.create=true",
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LongIdRouteTests {

    @Autowired lateinit var strategies: RSocketStrategies
    @Autowired lateinit var composite: CompositeServiceBeans<Long, String>
    @Autowired lateinit var authentication: AuthenticationService<Long>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val callers by lazy { DeploymentCallers(strategies, port, composite, authentication) }

    private val admin by lazy { callers.connect("Admin", "changeme") }

    @Test
    fun `Admin reads a room by id`() {
        val room = callers.addRoom("longtopicbyid")

        val read = admin.route("topic.topic-by-id").data(ByIdRequest(room.id))
            .retrieveMono(Map::class.java).block(callers.timeout)!!

        assertThat(read.toString()).contains("longtopicbyid", room.id.toString())
    }

    @Test
    fun `Admin reads the members of a room`() {
        val room = callers.addRoom("longmembersroom")

        val members = admin.route("topic.topic-members").data(ByIdRequest(room.id))
            .retrieveMono(Map::class.java).block(callers.timeout)

        assertThat(members).isNotNull
    }

    @Test
    fun `Admin reads a user by id`() {
        val user = callers.standardUser("longbyiduser", "longbyidusersecret")

        val read = admin.route("user.user-by-id").data(ByIdRequest(user.id))
            .retrieveMono(Map::class.java).block(callers.timeout)!!

        assertThat(read.toString()).contains("longbyiduser", user.id.toString())
    }
}
