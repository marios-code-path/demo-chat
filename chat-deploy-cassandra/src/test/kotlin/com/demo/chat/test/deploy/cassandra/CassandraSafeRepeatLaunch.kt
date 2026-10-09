package com.demo.chat.test.deploy.cassandra

import com.demo.chat.ChatApp
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext

/** The launch surface of `CassandraMessageSendTests`, for the Cassandra safe-repeat classes. */
object CassandraSafeRepeatLaunch {
    fun start(nodeId: Int): ConfigurableApplicationContext {
        val container = CassandraContainerBase.cassandraContainer
        val args = listOf(
            "spring.config.location=classpath:/application.yml",
            "spring.config.additional-location=classpath:/config/logging.yml," +
                "classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
            "server.port=0",
            "spring.rsocket.server.port=0",
            "app.key.type=long",
            "app.nodeid=$nodeId",
            "app.users.create=true",
            "app.service.core.key=cassandra",
            "app.service.core.persistence=cassandra",
            "app.service.core.index=cassandra",
            "app.service.core.pubsub=memory",
            "app.service.core.secrets=cassandra",
            "app.service.composite",
            "app.service.composite.auth=true",
            "app.command.bus=memory",
            "app.controller.secrets",
            "app.controller.key",
            "app.controller.persistence",
            "app.controller.index",
            "app.controller.user",
            "app.controller.message",
            "app.controller.topic",
            "app.controller.pubsub",
            "app.service.security.userdetails",
            "spring.profiles.active=cassandra-contact-point",
            "spring.cassandra.contact-points=${container.host}",
            "spring.cassandra.port=${container.getMappedPort(9042)}",
            "spring.cassandra.username=${container.username}",
            "spring.cassandra.password=${container.password}",
        ).map { "--$it" }
        return SpringApplicationBuilder(ChatApp::class.java)
            .web(WebApplicationType.NONE)
            .run(*args.toTypedArray())
    }
}
