package com.demo.chat.test.rsocket.probe

import com.demo.chat.config.controller.composite.TopicServiceController
import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.test.access.ChatAccessTestConfiguration
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.TestCompositeServiceBeans
import com.demo.chat.test.key.TestKeys
import com.demo.chat.test.rsocket.RSocketSecurityTestConfiguration
import com.demo.chat.test.rsocket.RSocketTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito.given
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * The allowed caller at the RSocket boundary for `CHAT-znprrzhn`.
 *
 * **This is the control.** The mocked broker answers true, so a refusal here
 * means the proxy never saw the annotation. The delegate answers one room, so
 * the assertion reads a value and not an emptiness.
 *
 * **The delegate stub is replaced here.** The shared configuration answers an
 * empty stream, and an empty stream satisfies a careless assertion. A refusal
 * and an empty room list must not be the same answer.
 *
 * **`probe.caller` is a context key, and it is not read by any code.** Spring
 * caches a context by its configuration, and not by the test class. Without
 * this key this class and the denied probe share one context and one
 * `TestCompositeServiceBeans`. See the denied probe for the measured failure.
 */
@ContextConfiguration(
    classes = [
        TopicServiceController::class,
        RSocketSecurityTestConfiguration::class,
        ChatAccessTestConfiguration::class,
        ProbeServiceBeans::class,
    ]
)
@TestPropertySource(properties = ["app.controller.topic", "probe.caller=allowed"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RSocketBoundaryProbeAllowedTests : RSocketTestBase() {

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
    fun `an allowed caller reaches the service and listRooms runs once`() {
        given(accessBroker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(true))

        val room: MessageTopic<Long> = MessageTopic.Factory.create(TestKeys.key(1L), "room-one")
        given(probe.mockTopicBean.listRooms()).willReturn(Flux.just(room))

        val hits = requester.route("topic.topic-list")
            .retrieveFlux(MessageTopic::class.java)
            .collectList()
            .block(Duration.ofSeconds(10))

        assertThat(hits).describedAs("the allowed route answered").hasSize(1)
        verify(probe.mockTopicBean, times(1)).listRooms()
    }
}
