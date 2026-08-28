package com.demo.chat.test.domain

import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdConfiguration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.mock.env.MockEnvironment

class NodeIdConfigurationTests {

    private fun contextWith(nodeId: String?): AnnotationConfigApplicationContext {
        val context = AnnotationConfigApplicationContext()
        val environment = MockEnvironment()
        if (nodeId != null) {
            environment.setProperty("app.nodeid", nodeId)
        }
        context.environment = environment
        context.register(NodeIdConfiguration::class.java)
        context.refresh()
        return context
    }

    private fun allMessages(thrown: Throwable): String =
        generateSequence(thrown) { it.cause }.mapNotNull { it.message }.joinToString(" | ")

    @Test
    fun `a context without app nodeid fails and names the property`() {
        val thrown = Assertions.assertThrows(Exception::class.java) { contextWith(null) }
        val text = allMessages(thrown)
        Assertions.assertTrue(text.contains("app.nodeid is required"), text)
        Assertions.assertTrue(text.contains("unset"), text)
    }

    @Test
    fun `a context with a value out of range fails and names the range`() {
        val thrown = Assertions.assertThrows(Exception::class.java) { contextWith("1024") }
        Assertions.assertTrue(allMessages(thrown).contains("0..1023"))
    }

    @Test
    fun `a context with a valid app nodeid supplies the bean`() {
        contextWith("5").use { context ->
            Assertions.assertEquals(5, context.getBean(NodeId::class.java).value)
        }
    }

    @Test
    fun `a context accepts zero as an explicit value`() {
        contextWith("0").use { context ->
            Assertions.assertEquals(0, context.getBean(NodeId::class.java).value)
        }
    }
}
