package com.demo.chat.test

import com.demo.chat.service.core.IKeyService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = [
        "app.service.core.key=long", "server.port=0", "management.endpoints.enabled-by-default=false",
        "app.client.rsocket.core.persistence", "spring.rsocket.server.port=7890",
        "spring.cloud.service-registry.auto-registration.enabled=false", "app.rsocket.client.requester.factory=test",
        "spring.shell.interactive.enabled=false"]
)
class TracedResponderTests {

    private val log: Logger = LoggerFactory.getLogger(TracedResponderTests::class.java)

    @MockBean
    private lateinit var keyService: IKeyService<UUID>
    private val svcPrefix: String = "key."


    @Test
    fun `test context loads`() {
        log.info("TEST CONTEXT")
    }
}