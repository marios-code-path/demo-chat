import com.demo.chat.init.BaseApp
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

// Tests instances of client configurations with a dummy server
// configured just before RequesterFactory = see TestClientServerConfiguration
@SpringBootTest(classes = [BaseApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = [
        "app.service.core.key=long", "spring.cloud.consul.config.enabled=false", "app.client.rsocket",
        "app.primary=init", "server.port=0", "management.endpoints.enabled-by-default=false",
        "app.client.rsocket.core.persistence", "app.client.rsocket.core.index", "app.client.rsocket.core.pubsub",
        "app.client.rsocket.core.secrets", "spring.rsocket.server.port=7890",
        "spring.cloud.service-registry.auto-registration.enabled=false","app.rsocket.client.requester.factory=test",
        "spring.shell.interactive.enabled=false"]
)
@ActiveProfiles("init")
//@Import(TestConfigurationRSocket::class)
class ChatInitTests {

    @Test
    fun contextLoads() {
    }
}