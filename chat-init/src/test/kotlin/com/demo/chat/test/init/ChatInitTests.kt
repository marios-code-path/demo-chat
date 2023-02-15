import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.composite.UserServiceController
import com.demo.chat.controller.core.KeyServiceController
import com.demo.chat.controller.core.PersistenceServiceController
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.User
import com.demo.chat.init.BaseApp
import com.demo.chat.service.core.*
import com.demo.chat.service.dummy.DummyIndexService
import com.demo.chat.service.dummy.DummyKeyService
import com.demo.chat.service.dummy.DummyPersistenceStore
import com.demo.chat.test.rsocket.TestConfigurationRSocket
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.util.*

// TODO: use the dummyservers or mock the services
// configured just before RequesterFactory = see TestClientServerConfiguration
// TODO: refactor to use test components instead of PROD ones
@SpringBootTest(classes = [BaseApp::class, TestConfigurationRSocket::class, TestDummyControllerServices::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = [
        "app.service.core.key=long", "app.client.rsocket", "app.primary=init",
        "app.rsocket.transport.unprotected", "app.client.rsocket.core.key",
        "app.client.rsocket.core.persistence", "app.client.rsocket.core.index", "app.client.rsocket.core.pubsub",
        "app.client.rsocket.core.secrets", "app.client.rsocket.composite.user", "app.client.rsocket.composite.message",
        "app.client.rsocket.composite.topic", "app.service.composite.auth",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.cloud.consul.config.enabled=false", "spring.rsocket.server.port=0", "server.port=0",
        "spring.shell.interactive.enabled=false", "management.endpoints.enabled-by-default=false",
    ]
)
@ActiveProfiles("init")
class ChatInitTests {

    @Test
    fun contextLoads() {
    }
}

@TestConfiguration
class TestDummyControllerServices {

    class DummyUserIndex<T, Q>(that: IndexService<T, User<T>, Q>) : UserIndexService<T, Q>,
        IndexService<T, User<T>, Q> by that

    class DummyUserPersistence<T>(that: PersistenceStore<T, User<T>>) : UserPersistence<T>,
        PersistenceStore<T, User<T>> by that

    @Controller
    @MessageMapping("key")
    class TestKeyController<T>() : KeyServiceController<T>(DummyKeyService<T>())

    @Controller
    @MessageMapping("user")
    class TestUserController<T>() : UserServiceController<T, Map<String, String>>(
        DummyUserPersistence(DummyPersistenceStore()),
        DummyUserIndex(DummyIndexService<T, User<T>, Map<String, String>>()),
        java.util.function.Function { i -> mapOf(Pair(UserIndexService.HANDLE, i.handle)) })

    class DummyAuthMetadataPersistence<T>(that: PersistenceStore<T, AuthMetadata<T>>) : PersistenceStore<T, AuthMetadata<T>> by that

    @Controller
    @MessageMapping("persist.authmetadata")
    class AuthMetaPersistenceController<T, V>() :
        PersistenceServiceController<T, AuthMetadata<T>>(DummyAuthMetadataPersistence(DummyPersistenceStore()))
}