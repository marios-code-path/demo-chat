package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.DuplicateException
import com.demo.chat.domain.UserCreateRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import reactor.test.StepVerifier
import java.time.Duration

/** Check the composite services with real memory stores and Lucene indexes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-exact-identity",
        "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory", "app.service.core.pubsub=memory",
        "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite",
        "app.command.bus=memory", "app.service.composite.auth=true",
        "app.users.create=true",
    ]
)
class ExactIdentityServiceTests {
    @Autowired
    lateinit var composite: CompositeServiceBeans<Long, String>

    private val timeout = Duration.ofSeconds(10)

    @Test
    fun `addUser compares whole handles and ignores case`() {
        val first = composite.userService().addUser(UserCreateRequest("First", "whole10-user", "http://u")).block(timeout)!!
        val second = composite.userService().addUser(UserCreateRequest("Second", "whole2-user", "http://u")).block(timeout)!!
        assertThat(first).isNotEqualTo(second)

        StepVerifier.create(composite.userService().addUser(UserCreateRequest("Again", "whole10-user", "http://u")))
            .expectError(DuplicateException::class.java)
            .verify()

        composite.userService().addUser(UserCreateRequest("Case", "CaseGuard", "http://u")).block(timeout)
        StepVerifier.create(composite.userService().addUser(UserCreateRequest("Again", "caseguard", "http://u")))
            .expectError(DuplicateException::class.java)
            .verify()
    }

    @Test
    fun `addRoom and getRoomByName compare whole names`() {
        val first = composite.topicService().addRoom(ByStringRequest("whole10-user:room")).block(timeout)!!
        val second = composite.topicService().addRoom(ByStringRequest("whole2-user:room")).block(timeout)!!
        assertThat(first).isNotEqualTo(second)
        assertThat(composite.topicService().getRoomByName(ByStringRequest("whole2-user:room")).block(timeout)!!.key)
            .isEqualTo(second)

        StepVerifier.create(composite.topicService().addRoom(ByStringRequest("whole10-user:room")))
            .expectError(DuplicateException::class.java)
            .verify()
    }
}
