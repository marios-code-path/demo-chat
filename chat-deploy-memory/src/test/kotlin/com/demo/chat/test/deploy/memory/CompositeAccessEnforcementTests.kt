package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.security.access.composite.TopicServiceAccess
import io.rsocket.exceptions.ApplicationErrorException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import reactor.test.StepVerifier
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/** The deployed composite access seam. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-composite-access",
        "app.server.proto=rsocket", "server.port=0", "spring.rsocket.server.port=0",
        "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory", "app.service.core.pubsub=memory",
        "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory",
        "app.service.composite", "app.service.composite.auth",
        "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic",
        "app.controller.pubsub",
        "app.service.security.userdetails", "app.users.create=true"
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompositeAccessEnforcementTests {

    @Autowired lateinit var applicationContext: ApplicationContext
    @Autowired lateinit var stores: PersistenceServiceBeans<Long, String>
    @Autowired lateinit var indexes: IndexServiceBeans<Long, String, IndexSearchRequest>

    lateinit var requester: RSocketRequester

    private val timeout = Duration.ofSeconds(10)
    private val roomName = "enforcedroom"

    @BeforeAll
    internal fun `connect to the deployment`(
        @Autowired builder: RSocketRequester.Builder,
        @Value("\${local.rsocket.server.port}") port: Int,
    ) {
        val mapper = JsonMapper.builder()
            .addModule(ChatJackson3Modules().chatJackson3Module())
            .build()

        requester = builder
            .rsocketStrategies { sb -> sb.decoders { it.add(0, JacksonJsonDecoder(mapper)) } }
            .tcp("localhost", port)
    }

    @Test
    fun `the deployment registers a proxied topic controller`() {
        val access = applicationContext.getBean(TopicServiceAccess::class.java)

        assertThat(AopUtils.isAopProxy(access))
            .describedAs("the topic controller proxy")
            .isTrue()
    }

    @Test
    fun `a caller holding no room grant is refused at the route and writes nothing`() {
        StepVerifier.create(
            requester.route("topic.topic-add")
                .data(ByStringRequest(roomName))
                .retrieveMono(Key::class.java)
        ).expectErrorSatisfies { error ->
            assertThat(error)
                .describedAs("the wire form of the refusal")
                .isInstanceOf(ApplicationErrorException::class.java)
                .hasMessageContaining("Access Denied")
        }.verify(timeout)

        assertThat(
            indexes.topicIndex().findBy(IndexSearchRequest(TopicIndexService.NAME, roomName, 100))
                .collectList().block(timeout)
        ).describedAs("the index rows for the refused room").isEmpty()

        assertThat(
            stores.topicPersistence().all()
                .filter { it.data == roomName }
                .collectList().block(timeout)
        ).describedAs("the stored rooms that the refused call names").isEmpty()
    }
}
