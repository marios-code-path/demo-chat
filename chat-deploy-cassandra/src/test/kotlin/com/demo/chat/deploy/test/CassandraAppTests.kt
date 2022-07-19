package com.demo.chat.deploy.test

import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.CassandraTestConfiguration
import com.demo.chat.test.CassandraTestContainerConfiguration
import com.demo.chat.test.TestUUIDKeyGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    classes = [CassandraTestConfiguration::class]
)
class CassandraAppTests  : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {
    @Test
    fun `should application context load`() {

    }
}
