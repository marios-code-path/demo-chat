package com.demo.chat.test.deploy.cassandra

import com.datastax.oss.driver.api.core.CqlSessionBuilder
import com.demo.chat.config.deploy.cassandra.dse.ContactPointConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.boot.cassandra.autoconfigure.CassandraProperties
import org.springframework.data.cassandra.config.AbstractSessionConfiguration
import org.springframework.data.cassandra.config.SessionBuilderConfigurer

class ContactPointConfigurationTests {

    @Test
    fun `configured contact points and port replace Spring Data defaults`() {
        val props = CassandraProperties().apply {
            contactPoints = listOf("node-a.example", "node-b.example")
            port = 19042
        }

        val configuration = ContactPointConfiguration(props)

        assertEquals("node-a.example,node-b.example", baseValue(configuration, "getContactPoints"))
        assertEquals(19042, baseValue(configuration, "getPort"))
    }

    @Test
    fun `session configurer does not append a contact point`() {
        val props = CassandraProperties().apply {
            contactPoints = listOf("node-a.example")
            port = 19042
        }
        val builder = mock(CqlSessionBuilder::class.java, Answers.RETURNS_SELF)

        val configuration = ContactPointConfiguration(props)
        val configurer = ContactPointConfiguration::class.java
            .getDeclaredMethod("getSessionBuilderConfigurer")
            .apply { isAccessible = true }
            .invoke(configuration) as SessionBuilderConfigurer
        configurer.configure(builder)

        verifyNoInteractions(builder)
    }

    private fun baseValue(configuration: ContactPointConfiguration, methodName: String): Any? =
        AbstractSessionConfiguration::class.java.getDeclaredMethod(methodName)
            .apply { isAccessible = true }
            .invoke(configuration)
}
