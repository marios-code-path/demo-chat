package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.service.security.AuthenticationService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
 * `messageById` reads the message, and then checks `SUBSCRIBE` on its room.
 * See `CHAT-erkoxybm`. Before this rule, the shipped `{Anon, Message, GET}`
 * row let every caller read every message by id.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-message-by-id-room-rule",
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
class MessageByIdRoomRuleTests {

    @Autowired lateinit var strategies: RSocketStrategies
    @Autowired lateinit var composite: CompositeServiceBeans<Long, String>
    @Autowired lateinit var authentication: AuthenticationService<Long>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val callers by lazy { DeploymentCallers(strategies, port, composite, authentication) }

    @Test
    fun `a member reads a message by id and a caller that is not a member is refused`() {
        val room = callers.addRoom("byidruleroom")
        val member = callers.standardUser("byidmember", "byidmembersecret")
        val outsider = callers.standardUser("byidoutsider", "byidoutsidersecret")
        val memberRequester = callers.connect("byidmember", "byidmembersecret")
        callers.join(memberRequester, member, room)
        val message = callers.send(memberRequester, member, room, "read me by id")

        val read = memberRequester.route("message.message-by-id").data(ByIdRequest(message.id))
            .retrieveMono(Map::class.java).block(callers.timeout)
        assertThat(read).describedAs("the member read").isNotNull

        assertThatThrownBy {
            callers.connect("byidoutsider", "byidoutsidersecret").route("message.message-by-id")
                .data(ByIdRequest(message.id)).retrieveMono(Map::class.java).block(callers.timeout)
        }.describedAs("the outsider read").hasMessageContaining("Access Denied")
    }
}
