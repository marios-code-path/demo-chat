package com.demo.chat.test.deploy.cassandra

import com.demo.chat.deploy.cassandra.App
import com.demo.chat.index.cassandra.config.IndexServiceConfiguration
import com.demo.chat.persistence.cassandra.repository.ChatUserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [App::class]
)
@TestPropertySource(
    properties = [
        "app.primary=core", "server.port=0", "management.endpoints.enabled-by-default=false",
        "spring.shell.interactive.enabled=false", "app.service.core.key=uuid",
        "app.service.core.persistence", "app.service.core.secrets", "app.service.core.index"
    ]
)
class CassandraDeployTest : CassandraContainerBase() {
    @Autowired
    private lateinit var repo: ChatUserRepository<UUID>

    @Autowired
    private lateinit var indexConf: IndexServiceConfiguration<UUID>

    @Test
    fun contextLoads() {
        repo.findAll().collectList().block()
    }
}