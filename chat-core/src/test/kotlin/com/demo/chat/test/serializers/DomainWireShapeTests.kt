package com.demo.chat.test.serializers

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.test.TestBase
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DomainWireShapeTests : TestBase() {

    private fun shapeNode(obj: Any): JsonNode {
        mapper.apply { registerModules(DefaultChatJacksonModules().allModules()) }
        return mapper.readTree(mapper.writeValueAsString(obj))
    }

    @Test
    fun `User serialises flat without the user wrapper`() {
        val node = shapeNode(User.create(Key.funKey(1L), "MOON", "LUNA", "http://"))
        Assertions.assertFalse(node.has("user"), "User must not carry the user wrapper")
        Assertions.assertEquals("MOON", node.get("name").asText())
        Assertions.assertEquals("LUNA", node.get("handle").asText())
        Assertions.assertEquals("http://", node.get("imageUri").asText())
        Assertions.assertTrue(node.get("key").has("key"), "Key must stay wrapped")
    }

    @Test
    fun `TopicMembership serialises flat without the membership wrapper`() {
        val node = shapeNode(TopicMembership.create(1L, 2L, 3L))
        Assertions.assertFalse(node.has("membership"), "TopicMembership must not carry the membership wrapper")
        Assertions.assertEquals(1L, node.get("key").asLong())
        Assertions.assertEquals(2L, node.get("member").asLong())
        Assertions.assertEquals(3L, node.get("memberOf").asLong())
    }

    @Test
    fun `MessageTopic serialises with the inherited keyValue wrapper`() {
        val node = shapeNode(MessageTopic.create(Key.funKey(1L), "MOON"))
        Assertions.assertTrue(node.has("keyValue"), "MessageTopic inherits the keyValue wrapper")
        Assertions.assertFalse(node.has("topic"), "MessageTopic must not carry the topic wrapper")
        Assertions.assertEquals("MOON", node.get("keyValue").get("data").asText())
    }
}