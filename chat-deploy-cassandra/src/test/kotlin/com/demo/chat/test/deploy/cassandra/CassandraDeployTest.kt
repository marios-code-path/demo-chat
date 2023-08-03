package com.demo.chat.test.deploy.cassandra

import com.demo.chat.config.index.cassandra.IndexServiceConfiguration
import com.demo.chat.deploy.cassandra.App
import com.demo.chat.persistence.cassandra.repository.ChatUserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [App::class]
)
@TestPropertySource(
    properties = [
        "spring.config.location=classpath:/application.yml",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long",
        "app.service.core.key",
        "app.service.core.pubsub", "app.service.core.index", "app.service.core.persistence",
        "app.service.core.secrets", "app.service.composite",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub"
    ]
)
class CassandraDeployTest : CassandraContainerBase() {
    @Autowired
    private lateinit var repo: ChatUserRepository<Long>

    @Autowired
    private lateinit var indexConf: IndexServiceConfiguration<Long>

    @Test
    fun contextLoads() {
        repo.findAll().collectList().block()
    }
}