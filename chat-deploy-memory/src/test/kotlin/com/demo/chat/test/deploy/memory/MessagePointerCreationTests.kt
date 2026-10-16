package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessagePointerRequest
import com.demo.chat.domain.PointerMessage
import com.demo.chat.service.security.AuthenticationService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.util.concurrent.atomic.AtomicInteger

/**
 * Each refusal of pointer creation, and the control. See `CHAT-erkoxybm`.
 *
 * Every call crosses the RSocket seam and the composite access checks of a
 * running composition root. After each refusal, the host room holds no new
 * pointer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-message-pointer-creation",
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
class MessagePointerCreationTests {

    @Autowired lateinit var strategies: RSocketStrategies
    @Autowired lateinit var stores: PersistenceServiceBeans<Long, String>
    @Autowired lateinit var composite: CompositeServiceBeans<Long, String>
    @Autowired lateinit var authentication: AuthenticationService<Long>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val callers by lazy { DeploymentCallers(strategies, port, composite, authentication) }
    private val tags = AtomicInteger()

    /** A member of [host] and [source]. [message] sits in [source], so the member can read it. */
    private inner class Scene {
        val tag = "s${tags.incrementAndGet()}"
        val host = callers.addRoom("ptrhost$tag")
        val source = callers.addRoom("ptrsource$tag")
        val member = callers.standardUser("ptrmember$tag", "ptrmembersecret$tag")
        val requester = callers.connect("ptrmember$tag", "ptrmembersecret$tag")

        init {
            callers.join(requester, member, host)
            callers.join(requester, member, source)
        }

        val message = callers.send(requester, member, source, "pointer target $tag")
    }

    @Test
    fun `an anonymous caller is refused`() {
        val scene = Scene()
        assertRefused(callers.connect(null, null), request(scene.host, scene.message.id, "Message"), "Access Denied", scene.host)
    }

    @Test
    fun `a caller without FORWARD on the host room is refused`() {
        val scene = Scene()
        val outsider = callers.standardUser("ptroutsider${scene.tag}", "ptroutsidersecret")
        val outsiderRequester = callers.connect("ptroutsider${scene.tag}", "ptroutsidersecret")
        // The outsider can read the target, so only FORWARD on the host room is missing.
        callers.join(outsiderRequester, outsider, scene.source)
        assertRefused(outsiderRequester, request(scene.host, scene.message.id, "Message"), "Access Denied", scene.host)
    }

    @Test
    fun `a target domain other than Message or MessageTopic is refused`() {
        val scene = Scene()
        assertRefused(scene.requester, request(scene.host, scene.message.id, "User"), "Message or MessageTopic", scene.host)
    }

    @Test
    fun `a target message that does not exist is refused`() {
        val scene = Scene()
        assertRefused(scene.requester, request(scene.host, UNKNOWN_ID, "Message"), "no such message", scene.host)
    }

    @Test
    fun `a target message in a room the caller cannot read is refused as missing`() {
        val scene = Scene()
        val secretRoom = callers.addRoom("ptrsecret${scene.tag}")
        val other = callers.standardUser("ptrother${scene.tag}", "ptrothersecret")
        val otherRequester = callers.connect("ptrother${scene.tag}", "ptrothersecret")
        callers.join(otherRequester, other, secretRoom)
        val secret = callers.send(otherRequester, other, secretRoom, "not for the member")
        assertRefused(scene.requester, request(scene.host, secret.id, "Message"), "no such message", scene.host)
    }

    @Test
    fun `a target topic that does not exist is refused`() {
        val scene = Scene()
        assertRefused(scene.requester, request(scene.host, UNKNOWN_ID, "MessageTopic"), "no such topic", scene.host)
    }

    @Test
    fun `a target topic the caller cannot read is refused as missing`() {
        val scene = Scene()
        val closed = callers.addRoom("ptrclosed${scene.tag}")
        assertRefused(scene.requester, request(scene.host, closed.id, "MessageTopic"), "no such topic", scene.host)
    }

    /**
     * `deleteRoom` removes the store row and the index row. The key registry
     * keeps the key, so a registry check alone passes. The member still holds
     * its `SUBSCRIBE` row. This test removes the store row the same way.
     */
    @Test
    fun `a target topic whose store row is gone is refused as missing`() {
        val scene = Scene()
        val gone = callers.addRoom("ptrgone${scene.tag}")
        callers.join(scene.requester, scene.member, gone)
        stores.topicPersistence().rem(gone).block(callers.timeout)
        assertRefused(scene.requester, request(scene.host, gone.id, "MessageTopic"), "no such topic", scene.host)
    }

    @Test
    fun `a pointer to a pointer is refused`() {
        val scene = Scene()
        create(scene.requester, request(scene.host, scene.message.id, "Message"))
        val first = pointersIn(scene.host).single()
        assertRefused(scene.requester, request(scene.host, first.key.id, "Message"), "a pointer cannot target a pointer", scene.host, before = 1)
    }

    @Test
    fun `a member with FORWARD and a readable target stores one pointer per target`() {
        val scene = Scene()
        create(scene.requester, request(scene.host, scene.message.id, "Message"))
        create(scene.requester, request(scene.host, scene.source.id, "MessageTopic"))

        val stored = pointersIn(scene.host)
        assertThat(stored.map { it.target.id }).containsExactlyInAnyOrder(scene.message.id, scene.source.id)
        assertThat(stored.map { it.key.from }).containsOnly(scene.member.id)
    }

    private fun request(host: Key<Long>, targetId: Long, domain: String) =
        MessagePointerRequest(host.id, targetId, domain, "ptr-${tags.incrementAndGet()}")

    private fun create(requester: RSocketRequester, req: MessagePointerRequest<Long>) {
        requester.route("message.message-pointer").data(req).retrieveMono(Map::class.java).block(callers.timeout)
    }

    private fun assertRefused(requester: RSocketRequester, req: MessagePointerRequest<Long>, text: String, host: Key<Long>, before: Int = 0) {
        assertThatThrownBy { create(requester, req) }.describedAs("the refusal").hasMessageContaining(text)
        assertThat(pointersIn(host)).describedAs("the pointers in the host room after the refusal").hasSize(before)
    }

    @Suppress("UNCHECKED_CAST")
    private fun pointersIn(host: Key<Long>): List<PointerMessage<Long>> =
        stores.messagePersistence().all().collectList().block(callers.timeout)!!
            .filter { it is PointerMessage<*> && it.key.dest == host.id }
            .map { it as PointerMessage<Long> }

    companion object {
        /** An id that no registry holds. */
        const val UNKNOWN_ID = 424242L
    }
}
