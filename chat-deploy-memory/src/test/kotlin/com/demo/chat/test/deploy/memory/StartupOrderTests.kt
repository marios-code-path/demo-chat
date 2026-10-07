package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.deploy.event.RootKeyInitializationReadyEvent
import com.demo.chat.index.lucene.LuceneIndexLoad
import com.demo.chat.service.core.StartupIndexLoad
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.rsocket.context.RSocketServerInitializedEvent
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.server.context.WebServerInitializedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.util.Collections

/**
 * Both servers start only after the start sequence completes. See
 * `CHAT-bafkgkko`, D2.
 *
 * `RootKeyStartup` publishes readiness only after every index load completes,
 * and a failed load publishes nothing. So readiness before each server event
 * means that the auth index load completed before either server accepted a
 * connection. The memory deployment claims no node id. See
 * docs/NODEID-CLAIM.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [ChatApp::class])
@Import(StartupOrderTests.Recorder::class)
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-startup-order", "app.server.proto=rsocket",
        "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory", "app.service.core.pubsub=memory", "app.service.core.index=lucene",
        "app.service.core.persistence=memory", "app.service.core.secrets=memory",
        "app.service.composite", "app.service.composite.auth=true",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.service.security.userdetails", "app.users.create=true",
    ]
)
class StartupOrderTests {

    @TestConfiguration
    class Recorder {
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf())

        // Anonymous objects keep their generic type, so each listener receives one event type.
        @Bean
        fun readinessRecorder() = object : ApplicationListener<RootKeyInitializationReadyEvent<*>> {
            override fun onApplicationEvent(event: RootKeyInitializationReadyEvent<*>) {
                events += "ready"
            }
        }

        @Bean
        fun webServerRecorder() = object : ApplicationListener<WebServerInitializedEvent> {
            override fun onApplicationEvent(event: WebServerInitializedEvent) {
                events += "web server"
            }
        }

        @Bean
        fun rsocketServerRecorder() = object : ApplicationListener<RSocketServerInitializedEvent> {
            override fun onApplicationEvent(event: RSocketServerInitializedEvent) {
                events += "rsocket server"
            }
        }
    }

    @Autowired
    lateinit var recorder: Recorder

    @Autowired
    lateinit var loads: List<StartupIndexLoad>

    @Test
    fun `the auth index loads before either server starts`() {
        // The production auth index load is present, so readiness waited for it.
        assertThat(loads).anyMatch { it is LuceneIndexLoad<*, *> }

        val events = recorder.events.toList()
        assertThat(events).contains("ready", "web server", "rsocket server")
        assertThat(events.indexOf("ready")).isLessThan(events.indexOf("web server"))
        assertThat(events.indexOf("ready")).isLessThan(events.indexOf("rsocket server"))
    }
}
