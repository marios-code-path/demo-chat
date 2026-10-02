package com.demo.chat.test.rsocket.probe

import com.demo.chat.config.controller.composite.MessageServiceController
import com.demo.chat.config.controller.composite.TopicServiceController
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.test.access.ChatAccessTestConfiguration
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.TestCompositeServiceBeans
import com.demo.chat.test.key.TestKeys
import com.demo.chat.test.rsocket.RSocketSecurityTestConfiguration
import com.demo.chat.test.rsocket.RSocketTestBase
import io.rsocket.exceptions.ApplicationErrorException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration

/**
 * The denied caller at the RSocket boundary for `CHAT-znprrzhn`.
 *
 * `topic-list` carries `hasAccessToDomain('MessageTopic', 'GET_ALL')`. The mocked
 * broker answers false. So the proxy must refuse, and the delegate must never
 * see the call.
 *
 * **The refusal alone proves nothing.** An unevaluable expression also refuses.
 * The zero downstream assertion is what separates a refusal from a broken route.
 *
 * **The refusal crosses the wire as `ApplicationErrorException`, and not as
 * `AccessDeniedException`.** The server throws
 * `AuthorizationDeniedException: Access Denied`, and no RSocket exception
 * handler claims it, so the transport reports it as application error 0x201.
 * A client reads the type and the message, and never the server class. Every
 * other RSocket refusal test in this repository reads the same type.
 *
 * **`probe.caller` is a context key, and it is not read by any code.** Spring
 * caches a context by its configuration, and not by the test class. This class
 * and the allowed probe declare the same configuration, so without this key
 * they share one context and one `TestCompositeServiceBeans`. Spring resets a
 * `@MockitoBean` between methods, and that shared delegate is not one. So the
 * one `listRooms()` call of the allowed probe stays on the delegate, and the
 * `never()` assertion below reads it. Measured on 2026-10-01: the pair fails
 * and this class alone passes.
 *
 * **One route in this class carries no check, and that is deliberate.** The
 * owner decided on 2026-10-02 that `topic-by-name` answers every caller that
 * matches a room. A name holds no target key, so no check can run at the
 * route. The route answers the room key and the room name, and the access
 * condition travels with the operation the caller runs next. The last test
 * below pins that openness against this class's denying broker.
 */
@ContextConfiguration(
    classes = [
        TopicServiceController::class,
        MessageServiceController::class,
        RSocketSecurityTestConfiguration::class,
        ChatAccessTestConfiguration::class,
        ProbeServiceBeans::class,
    ]
)
@TestPropertySource(properties = ["app.controller.topic", "app.controller.message", "probe.caller=denied"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RSocketBoundaryProbeDeniedTests : RSocketTestBase() {

    @MockitoBean private lateinit var accessBroker: AccessBroker<Long>

    /**
     * `RSocketServerTestConfiguration` carries a bare `@ComponentScan`, so the
     * `TestSecretStoreController` of `SecretsControllerTests` enters this
     * context. It needs this store. Without the mock the context does not load.
     */
    @MockitoBean private lateinit var secretStore: SecretsStore<Long>

    /** The same object that `b.topicService()` answers. */
    @Autowired private lateinit var probe: TestCompositeServiceBeans<Long, String>

    @Test
    fun `a denied caller is refused and listRooms is never called`() {
        given(accessBroker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(false))

        StepVerifier.create(
            requester.route("topic.topic-list").retrieveFlux(MessageTopic::class.java)
        ).expectErrorSatisfies { error ->
            assertThat(error)
                .describedAs("the wire form of the refusal")
                .isInstanceOf(ApplicationErrorException::class.java)
                .hasMessageContaining("Access Denied")
        }.verify(Duration.ofSeconds(10))

        verify(probe.mockTopicBean, never()).listRooms()
    }

    @Test
    fun `a denied caller is refused for an object route and getRoom is never called`() {
        StepVerifier.create(
            requester.route("topic.topic-by-id")
                .data(ByIdRequest(999999L))
                .retrieveMono(MessageTopic::class.java)
        ).expectErrorSatisfies { error ->
            assertThat(error)
                .isInstanceOf(ApplicationErrorException::class.java)
                .hasMessageContaining("Access Denied")
        }.verify(Duration.ofSeconds(10))

        verify(probe.mockTopicBean, never()).getRoom(anyObject())
    }

    /**
     * **The room by name route carries no access expression.** The owner
     * decided on 2026-10-02 that it answers every caller that matches a room.
     *
     * The mocked broker denies everything here, so a refusal would mean a
     * check still guards the route. The delegate call and the answered room
     * are what prove the route is open.
     *
     * The answer holds the room key and the room name, and nothing else.
     * `MessageTopic` carries exactly those two fields, so no new shape is
     * needed to keep the answer minimal.
     */
    @Test
    fun `the room by name route carries no check and reaches the delegate`() {
        given(accessBroker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(false))

        val room: MessageTopic<Long> = MessageTopic.Factory.create(TestKeys.key(42L), "unguardedroom")
        given(probe.mockTopicBean.getRoomByName(anyObject())).willReturn(Mono.just(room))

        val answer = requester.route("topic.topic-by-name")
            .data(ByStringRequest("unguardedroom"))
            .retrieveMono(MessageTopic::class.java)
            .block(Duration.ofSeconds(10))
            ?: error("the unguarded route answered nothing")

        assertThat(answer.data).describedAs("the room name").isEqualTo("unguardedroom")
        assertThat(answer.key.id).describedAs("the room key id").isEqualTo(42L)
        verify(probe.mockTopicBean, times(1)).getRoomByName(anyObject())
    }

    @Test
    fun `a denied caller is refused for message send and send is never called`() {
        given(accessBroker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(false))

        StepVerifier.create(
            requester.route("message.message-send")
                .data(MessageSendRequest("body", 1L, 7L))
                .retrieveMono(Key::class.java)
        ).expectErrorSatisfies { error ->
            assertThat(error)
                .isInstanceOf(ApplicationErrorException::class.java)
                .hasMessageContaining("Access Denied")
        }.verify(Duration.ofSeconds(10))

        verify(probe.mockMessageBean, never()).send(anyObject())
    }
}
