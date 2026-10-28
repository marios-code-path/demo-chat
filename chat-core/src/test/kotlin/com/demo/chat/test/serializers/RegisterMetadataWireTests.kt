package com.demo.chat.test.serializers

import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.domain.RegisterCollisionRequest
import com.demo.chat.domain.RegisterCollisionResult
import com.demo.chat.domain.RegisterCollisionStatus
import com.demo.chat.domain.RegisterOwnerResult
import com.demo.chat.domain.RegisterOwnerStatus
import com.demo.chat.domain.RegisterRoomResult
import com.demo.chat.domain.RegisterRoomStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class RegisterMetadataWireTests {
    private val jackson2 = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModules(DefaultChatJacksonModules().allModules())
    private val jackson3 = JsonMapper.builder()
        .addModule(ChatJackson3Modules().chatJackson3Module())
        .build()

    @Test
    fun `Jackson 2 round trips each register metadata type`() {
        assertWire { value, type ->
            val json = jackson2.writeValueAsString(value)
            json to jackson2.readValue(json, type)
        }
    }

    @Test
    fun `Jackson 3 round trips each register metadata type`() {
        assertWire { value, type ->
            val json = jackson3.writeValueAsString(value)
            json to jackson3.readValue(json, type)
        }
    }

    private fun assertWire(roundTrip: (Any, Class<Any>) -> Pair<String, Any>) {
        val values = listOf(
            RegisterCollisionRequest("demo-chat", setOf("demo-chat-fr-a")),
            RegisterCollisionResult(RegisterCollisionStatus.CLEAR, null, null),
            RegisterCollisionResult(RegisterCollisionStatus.ROOM_HELD, "demo-chat-fr-b", null),
            RegisterCollisionResult(RegisterCollisionStatus.REGISTER_HELD, null, "demo"),
            RegisterRoomResult(RegisterRoomStatus.FOUND, "room-1"),
            RegisterRoomResult(RegisterRoomStatus.NOT_FOUND, null),
            RegisterRoomResult(RegisterRoomStatus.AMBIGUOUS_NAME, null),
            RegisterOwnerResult(RegisterOwnerStatus.FOUND, "user-1", "Mario"),
            RegisterOwnerResult(RegisterOwnerStatus.NO_OWNER, null, null),
            RegisterOwnerResult(RegisterOwnerStatus.MULTIPLE_OWNERS, null, null),
        )

        values.forEach { value ->
            @Suppress("UNCHECKED_CAST")
            val type = value.javaClass as Class<Any>
            val (json, decoded) = roundTrip(value, type)
            assertThat(decoded).isEqualTo(value)
            if (value is RegisterCollisionRequest) {
                assertThat(json).contains("\"type\":\"RegisterCollisionRequest\"")
            } else {
                assertThat(json).contains("\"status\"")
                when (value) {
                    is RegisterCollisionResult -> assertThat(json).contains("\"roomName\"", "\"registerName\"")
                    is RegisterRoomResult -> assertThat(json).contains("\"roomId\"")
                    is RegisterOwnerResult -> assertThat(json).contains("\"ownerId\"", "\"ownerHandle\"")
                }
            }
        }
    }
}
