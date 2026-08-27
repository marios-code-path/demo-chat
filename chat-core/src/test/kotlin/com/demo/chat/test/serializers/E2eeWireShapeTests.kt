package com.demo.chat.test.serializers

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.domain.*
import com.demo.chat.test.TestBase
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class E2eeWireShapeTests : TestBase() {

    private fun shapeNode(obj: Any): JsonNode {
        mapper.apply { registerModules(DefaultChatJacksonModules().allModules()) }
        return mapper.readTree(mapper.writeValueAsString(obj))
    }

    @Test
    fun `DeviceRegistration serialises flat without the device wrapper`() {
        val node = shapeNode(
            DeviceRegistration.create(
                Key.funKey(1L), Key.funKey(2L), 42,
                byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), 7
            )
        )
        Assertions.assertFalse(node.has("device"), "DeviceRegistration must not carry the device wrapper")
        Assertions.assertEquals(42, node.get("registrationId").asInt())
        Assertions.assertTrue(node.get("userId").has("key"), "Key must stay wrapped")
    }

    @Test
    fun `PreKeyBundle serialises flat without the preKeyBundle wrapper`() {
        val node = shapeNode(
            PreKeyBundle.create(
                Key.funKey(1L), Key.funKey(2L), Key.funKey(3L), 9,
                byteArrayOf(1), 4, byteArrayOf(2), byteArrayOf(3), byteArrayOf(4)
            )
        )
        Assertions.assertFalse(node.has("preKeyBundle"), "PreKeyBundle must not carry the preKeyBundle wrapper")
        Assertions.assertEquals(9, node.get("preKeyId").asInt())
    }

    @Test
    fun `EncryptedEnvelope serialises flat without the encryptedEnvelope wrapper`() {
        val node = shapeNode(
            EncryptedEnvelope.create(
                Key.funKey(1L), Key.funKey(2L), Key.funKey(3L), Key.funKey(4L), Key.funKey(5L),
                5L, MessageKind.PAIRWISE, byteArrayOf(1)
            )
        )
        Assertions.assertFalse(node.has("encryptedEnvelope"), "EncryptedEnvelope must not carry the encryptedEnvelope wrapper")
        Assertions.assertEquals(5L, node.get("seq").asLong())
        Assertions.assertEquals("PAIRWISE", node.get("messageKind").asText())
    }

    @Test
    fun `ConversationCursor serialises flat without the conversationCursor wrapper`() {
        val node = shapeNode(ConversationCursor.create(Key.funKey(1L), 3L))
        Assertions.assertFalse(node.has("conversationCursor"), "ConversationCursor must not carry the conversationCursor wrapper")
        Assertions.assertEquals(3L, node.get("nextSeq").asLong())
    }

    @Test
    fun `ConversationEpoch serialises flat without the conversationEpoch wrapper`() {
        val node = shapeNode(ConversationEpoch.create(Key.funKey(1L), Key.funKey(2L), 2))
        Assertions.assertFalse(node.has("conversationEpoch"), "ConversationEpoch must not carry the conversationEpoch wrapper")
        Assertions.assertEquals(2, node.get("epoch").asInt())
    }

    @Test
    fun `FrankingTag serialises flat without the frankingTag wrapper`() {
        val node = shapeNode(
            FrankingTag.create(
                Key.funKey(1L), Key.funKey(2L), 6L, Key.funKey(3L),
                MessageKind.SENDER_KEY, byteArrayOf(1), 11
            )
        )
        Assertions.assertFalse(node.has("frankingTag"), "FrankingTag must not carry the frankingTag wrapper")
        Assertions.assertEquals(11, node.get("frankingKeyId").asInt())
    }

    @Test
    fun `Presence serialises flat without the presence wrapper`() {
        val node = shapeNode(Presence.create(Key.funKey(1L), Key.funKey(2L), PresenceState.ONLINE))
        Assertions.assertFalse(node.has("presence"), "Presence must not carry the presence wrapper")
        Assertions.assertEquals("ONLINE", node.get("state").asText())
    }
}