import com.demo.chat.init.App
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [App::class])
@TestPropertySource(
    properties = [
        "app.service.core.key=long", "spring.cloud.consul.config.enabled=false",
        "app.primary=init", "server.port=0", "management.endpoints.enabled-by-default=false",
        "app.client.rsocket.core.persistence", "app.client.rsocket.core.index", "app.client.rsocket.core.pubsub",
        "app.client.rsocket.core.key",
        "spring.shell.interactive.enabled=false"]
)
class AppTests {

    @Test
    fun contextLoads() {
    }
}