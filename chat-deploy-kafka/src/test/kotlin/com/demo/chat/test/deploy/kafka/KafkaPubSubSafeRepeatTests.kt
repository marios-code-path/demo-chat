package com.demo.chat.test.deploy.kafka

import com.demo.chat.ChatApp
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.composite.command.handler.MessagePubSubHandler
import com.demo.chat.service.composite.command.publication.RoomPublications
import com.demo.chat.test.command.CompositionSafeRepeatBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import reactor.core.Disposable
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The `U` safe-repeat contract over pubsub `kafka`. The setup of
 * `KafkaDeploymentTests`, with `app.users.create=true`, because a command
 * names the `Admin` identity as its owner.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [ChatApp::class]
)
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    brokerPropertiesLocation = "classpath:kafka-test.properties",
)
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-kafka-safe-repeat",
        "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
        "app.service.core.key=memory",
        "app.service.core.pubsub=kafka",
        "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.command.bus=memory", "app.service.composite.auth=true",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence",
        "app.controller.index", "app.controller.user", "app.controller.message",
        "app.controller.topic", "app.controller.pubsub",
        "app.service.security.userdetails", "app.users.create=true",
    ]
)
class KafkaPubSubSafeRepeatTests : CompositionSafeRepeatBase() {
    @Autowired
    lateinit var applicationContext: ApplicationContext

    override fun context(): ApplicationContext = applicationContext

    override val roomName = "saferepeatkafkapubsub"

    /** Kafka delivers through a broker round trip, so the results need more time to show. */
    override val settle: Duration = Duration.ofSeconds(2)

    private val heard = CopyOnWriteArrayList<Long>()
    private var listener: Disposable? = null

    @Suppress("UNCHECKED_CAST")
    private val publications: RoomPublications<Long, String>
        get() = context().getBean(RoomPublications::class.java) as RoomPublications<Long, String>

    @BeforeEach
    fun listenOnceAndClear() {
        if (listener == null) {
            pubsubBeans.pubSubService().open(room).block(wait)
            listener = pubsubBeans.pubSubService().listenTo(room).subscribe { heard.add(it.key.id) }
            Thread.sleep(settle.toMillis())
        }
        heard.clear()
    }

    @AfterAll
    fun stopListening() {
        listener?.dispose()
    }

    override fun handler(): DomainCommandHandler<Long, String> = MessagePubSubHandler(publications)

    override fun results(command: AcceptedCommand<Long, String>): Int = heard.count { it == command.message.key.id }
}
