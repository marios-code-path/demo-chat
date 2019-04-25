package com.demo.chat

import com.demo.chat.domain.*
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
import java.util.stream.Stream

// Variances of Keys we want
data class TestAlertKey(
        override val id: UUID,
        override val roomId: UUID,
        override val timestamp: Instant
) : MessageAlertKey

data class TestTextKey(
        override val id: UUID,
        override val userId: UUID,
        override val roomId: UUID,
        override val timestamp: Instant
) : MessageTextKey

data class TestTextMessage(
        override val key: TestTextKey,
        override val value: String,
        override val visible: Boolean
) : TextMessage

data class TestInfoAlert(
        override val key: TestAlertKey,
        override val value: RoomInfo,
        override val visible: Boolean
) : InfoAlert


data class TestLeaveAlert(
        override val key: TestAlertKey,
        override val value: UUID,
        override val visible: Boolean
) : LeaveAlert

data class TestJoinAlert(
        override val key: TestAlertKey,
        override val value: UUID,
        override val visible: Boolean
) : JoinAlert

class MessageTests {

    private var counter = Random().nextInt()
    val mapper = ObjectMapper().registerModule(KotlinModule()).apply {
        propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        //configure(SerializationFeature.WRAP_ROOT_VALUE, true)
    }.findAndRegisterModules()!!

    private fun randomMessage(): Message<Any, Any> {

        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        counter++

        return if (counter % 2 == 0)
            TestJoinAlert(TestAlertKey(
                    messageId, roomId, Instant.now()
            ), userId, true)
        else
            TestTextMessage(TestTextKey(
                    messageId, userId, roomId, Instant.now()
            ), "Hello $counter !", true)
    }

    @Test
    fun `should ser or deser JSON objects from subtype`() {

    }

    @Test
    fun `Should serialize deserialize JSON from to Any Message`() {
        val messageJsons = ArrayList<String>()
        val messages = ArrayList<Message<MessageKey, Any>>()

        Stream.generate { randomMessage() }.limit(5)
                .forEach { msg ->
                    messageJsons.add(mapper.writeValueAsString(msg))
                }

        messageJsons
                .forEach {
                    val tree = mapper.readTree(it)

                    if (tree.fieldNames().hasNext()) {
                        val rootName = tree.fieldNames().next()

                        when (rootName) {
                            "TestTextMessage" -> messages.add(mapper.readValue<TestTextMessage>(it))
                            "TestJoinAlert" -> messages.add(mapper.readValue<TestJoinAlert>(it))
                        }
                    }

                }

        Assertions.assertThat(messages)
                .`as`("A collection of messages is present")
                .isNotNull
                .isNotEmpty

        messages
                .forEach { msg ->
                    when (msg) {
                        is TestTextMessage -> Assertions.assertThat(msg.key).`as`("Has expected message state")
                                .isNotNull
                                .hasFieldOrProperty("userId")
                        is TestJoinAlert -> Assertions.assertThat(msg.key).`as`("Has expected alert state")
                                .isNotNull
                                .hasFieldOrProperty("roomId")
                        else -> {
                            Assertions.assertThat(msg).`as`("Is a message")
                                    .isNotNull
                                    .hasFieldOrProperty("key")
                                    .hasFieldOrProperty("value")
                            Assertions.assertThat(msg).`as`("is a message key too")
                                    .isNotNull
                                    .hasFieldOrProperty("id")
                        }
                    }
                }
    }

    @Test
    fun `returns many message and smart casts`() {
        val messages = listOf(randomMessage(), randomMessage(),
                TestLeaveAlert(
                        TestAlertKey(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                Instant.now()
                        ), UUID.randomUUID(), true),
                randomMessage(), randomMessage(), randomMessage())

        messages.forEach { msg ->

            when (msg) {
                is TestTextMessage -> Assertions.assertThat(msg.key).`as`("Has expected message state")
                        .isNotNull
                        .hasFieldOrProperty("userId")
                is TestJoinAlert -> Assertions.assertThat(msg.key).`as`("Has expected alert state")
                        .isNotNull
                        .hasFieldOrProperty("roomId")
                else -> Assertions.assertThat(msg).`as`("Is a message, afte rall")
                        .isNotNull
                        .hasFieldOrProperty("key")
                        .hasFieldOrProperty("value")
            }
        }
    }

    @Test
    fun `should allow multi variance in type`() {
        val roomId = UUID.randomUUID()
        val messageId = UUID.randomUUID()

        val key = TestAlertKey(
                messageId,
                roomId,
                Instant.now()
        )

        val value = RoomInfo(
                1,
                2,
                1000
        )

        val message: Message<MessageKey, *> = TestInfoAlert(key, value, true)

        // Get some message and access specific fields
        if (message is TestInfoAlert) {
            Assertions.assertThat(message.key)
                    .`as`("key is consistent state")
                    .isNotNull
                    .hasFieldOrPropertyWithValue("roomId", roomId)

            Assertions.assertThat(message.value)
                    .`as`("value is consistent state")
                    .isNotNull
                    .hasFieldOrPropertyWithValue("totalMessages", 1000)
        }


    }
}