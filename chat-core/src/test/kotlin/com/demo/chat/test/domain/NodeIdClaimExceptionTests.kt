package com.demo.chat.test.domain

import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration

class NodeIdClaimExceptionTests {

    @Test
    fun `the redis message names the property the scope the holder and the wait`() {
        val text = NodeIdClaimException(
            NodeId(7),
            "redis store for key type long",
            "core-service@host-a:4711#a3f19c2b",
            Duration.ofSeconds(30)
        ).message!!

        Assertions.assertTrue(text.contains("app.nodeid=7 is already claimed"), text)
        Assertions.assertTrue(text.contains("the redis store for key type long"), text)
        Assertions.assertTrue(text.contains("Holder: core-service@host-a:4711#a3f19c2b"), text)
        Assertions.assertTrue(text.contains("wait 30s"), text)
    }

    @Test
    fun `the cassandra message names the keyspace`() {
        val text = NodeIdClaimException(
            NodeId(7),
            "cassandra keyspace chat_long",
            "core-service@host-b:5122#77c0aa41",
            Duration.ofSeconds(30)
        ).message!!

        Assertions.assertTrue(text.contains("the cassandra keyspace chat_long"), text)
        Assertions.assertFalse(text.contains("one store"), text)
    }
}
