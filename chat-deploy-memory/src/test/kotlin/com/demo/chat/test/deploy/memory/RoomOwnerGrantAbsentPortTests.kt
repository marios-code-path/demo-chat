package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder

class RoomOwnerGrantAbsentPortTests {

    @ParameterizedTest
    @ValueSource(strings = ["absent", "", "false"])
    fun `an RSocket server without composite auth fails at startup`(value: String) {
        assertThatThrownBy {
            SpringApplicationBuilder(ChatApp::class.java)
                .web(WebApplicationType.NONE)
                .properties(
                    "spring.application.name=test-deployment-room-owner-absent",
                    "app.server.proto=rsocket",
                    "server.port=0",
                    "spring.rsocket.server.port=0",
                    "app.key.type=long",
                    "app.nodeid=1",
                    "app.service.core.key=memory",
                    "app.service.core.pubsub=memory",
                    "app.service.core.index=lucene",
                    "app.service.core.persistence=memory",
                    "app.service.core.secrets=memory",
                    "app.service.composite=true", "app.command.bus=memory",
                    "app.controller.key=true",
                    "app.controller.persistence=true",
                    "app.controller.index=true",
                    "app.controller.user=true",
                    "app.controller.message=true",
                    "app.controller.topic=true",
                    "app.controller.pubsub=true",
                    "app.service.security.userdetails=true",
                )
                .run(*if (value == "absent") emptyArray() else arrayOf("--app.service.composite.auth=$value"))
                .use { }
        }.hasStackTraceContaining("An RSocket server requires app.service.composite.auth=true")
    }
}
