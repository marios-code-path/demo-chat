package com.demo.chat.domain.serializers

import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.TextMessageKey
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.tools.javac.util.Assert
import java.util.*


class TextMessageDeserializer : JsonDeserializer<TextMessage>() {

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): TextMessage {

        Assert.checkNonNull(jp)
        Assert.checkNonNull(ctxt)

        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val nodeText = node.asText()

        val text = node.get("value").asText()
        val visible = node.get("visible").asBoolean()

        val keyNode = node.get("key")
        val keyId = UUID.randomUUID() //keyNode.get("msgId").asText()
        val topicId = UUID.randomUUID() //UUID.fromString(keyNode.get("topicId").asText())
        val userId = UUID.randomUUID() //UUID.fromString(keyNode.get("userId").asText())

        return TextMessage.create(
                TextMessageKey.create(
                        keyId,
                        topicId,
                        userId
                ),
                text,
                visible
        )

    }

}