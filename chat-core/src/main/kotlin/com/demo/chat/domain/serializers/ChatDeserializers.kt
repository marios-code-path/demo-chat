package com.demo.chat.domain.serializers

import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.UserMessageKey
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import java.util.*

//
//class TextMessageDeserializer : JsonDeserializer<TextMessage>() {
//
//    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): TextMessage {
//
//        val oc: ObjectCodec = jp?.codec!!
//        val node: JsonNode = oc.readTree(jp)
//
//        val nodeText = node.asText()
//
//        val text = node.get("value").asText()
//        val visible = node.get("visible").asBoolean()
//
//        val keyNode = node.get("key")
//        val keyId = UUID.fromString(keyNode.get("id").asText())
//        val topicId = UUID.fromString(keyNode.get("topicId").asText())
//        val userId = UUID.fromString(keyNode.get("userId").asText())
//
//        return TextMessage.create(
//                UserMessageKey.create(
//                        keyId,
//                        topicId,
//                        userId
//                ),
//                text,
//                visible
//        )
//    }
//}