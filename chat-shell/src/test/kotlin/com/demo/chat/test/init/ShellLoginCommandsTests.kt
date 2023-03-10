package com.demo.chat.test.init

import com.demo.chat.shell.BaseApp
import com.demo.chat.shell.commands.LoginCommands
import com.demo.chat.shell.commands.UserCommands
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.*

class LongLoginCommandsTests : ShellLoginCommandsTests<Long>()

@SpringBootTest(classes = [BaseApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = [
        "app.key.type=long", "app.client.protocol=rsocket", "app.primary=test",
        "app.rsocket.transport.unprotected", "app.client.rsocket.core.key",
        "app.client.rsocket.core.persistence", "app.client.rsocket.core.index", "app.client.rsocket.core.pubsub",
        "app.client.rsocket.core.secrets", "app.client.rsocket.composite.user", "app.client.rsocket.composite.message",
        "app.client.rsocket.composite.topic", "app.service.composite.auth",
        "spring.cloud.service-registry.auto-registration.enabled=false", "app.client.rsocket.discovery.springsecurity",
        "spring.cloud.consul.config.enabled=false", "spring.rsocket.server.port=0", "server.port=0",
        "spring.shell.interactive.enabled=false", "management.endpoints.enabled-by-default=false",
    ]
)
@ActiveProfiles("shell")
@Testcontainers
@Disabled
open class ShellLoginCommandsTests<T> {

    companion object {
        val imageName = "core-services:0.0.1"

        @Container
        val container = GenericContainer("core-services:0.0.1").withExposedPorts(6790)
            .apply {
                start()
                    setWaitStrategy(
                        LogMessageWaitStrategy()
                            .withRegEx("^In-Memory Deployment Started.*") //Netty started on port
                            .withTimes(1)
                            .withStartupTimeout(Duration.ofSeconds(30))
                    )
            }

        @JvmStatic
        @DynamicPropertySource
        fun clientPropertySetup(registry: org.springframework.test.context.DynamicPropertyRegistry) {
            val hostPort = container.host.toString() +":"+ container.getMappedPort(6790).toString()
            val configPrefix = "app.rsocket.client.config"
            for(service in listOf("key", "persistence", "index", "pubsub", "secrets", "user", "message", "topic", "auth") ) {
                registry.add("$configPrefix.$service.dest") { hostPort }
            }
        }
    }

    @Test
    fun `should login succeed`(@Autowired loginCommands: LoginCommands<T>) {
        Assertions.catchThrowable { loginCommands.login("Anon", "_") }
    }

    @Test
    fun `should login fail`(@Autowired loginCommands: LoginCommands<T>) {
        Assertions.assertThatThrownBy { loginCommands.login("Anony", "_") }
    }

    @Test
    fun `should whoami succeed`(@Autowired loginCommands: LoginCommands<T>) {
        loginCommands.login("Anon", "_")
        Assertions.assertThat(loginCommands.whoami())
            .isNotNull
            .isNotBlank
            .contains("Anon")
    }


    @Test
    fun `should get key`(@Autowired userCommands: UserCommands<T>) {
        Assertions.assertThat(userCommands.key("false")).isNotNull
    }

    @Test
    fun `should get Users`(@Autowired userCommands: UserCommands<T>) {
        Assertions.assertThat(userCommands.users())
            .isNotNull
            .isNotBlank
            .containsAnyOf("Anon", "Admin")
    }

    @Test
    fun `should create user`(@Autowired userCommands: UserCommands<T>) {
        Assertions.assertThat(userCommands.addUser("Test", "TEST", "uri"))
            .isNotNull
            .isNotBlank
    }

}