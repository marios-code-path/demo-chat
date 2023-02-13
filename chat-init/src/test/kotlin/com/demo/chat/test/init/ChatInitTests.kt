import com.demo.chat.init.BaseApp
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

// TODO: use the dummyservers or mock the services
// configured just before RequesterFactory = see TestClientServerConfiguration
// TODO: refactor to use test components instead of PROD ones
@SpringBootTest(classes = [BaseApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = [
        "app.service.core.key=long", "app.client.rsocket", "app.primary=init",
        "app.rsocket.transport.unprotected", "app.client.rsocket.core.key",
        "app.client.rsocket.core.persistence", "app.client.rsocket.core.index", "app.client.rsocket.core.pubsub",
        "app.client.rsocket.core.secrets", "app.client.rsocket.composite.user", "app.client.rsocket.composite.message",
        "app.client.rsocket.composite.topic", "app.service.composite.auth",
        "app.client.rsocket.discovery.default", "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.cloud.consul.config.enabled=false", "spring.rsocket.server.port=7890", "server.port=0",
        "spring.shell.interactive.enabled=false", "management.endpoints.enabled-by-default=false",
    ]
)
@ActiveProfiles("init")
class ChatInitTests {

    @Test
    fun contextLoads() {
    }
}