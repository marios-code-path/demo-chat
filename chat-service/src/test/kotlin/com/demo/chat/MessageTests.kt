package com.demo.chat

import com.demo.chat.domain.*
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonTypeName
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


data class TestAlertMessageKey(
        override val id: UUID,
        override val topicId: UUID,
        override val timestamp: Instant
) : AlertMessageKey

data class TestTextMessageKey(
        override val id: UUID,
        override val userId: UUID,
        override val topicId: UUID,
        override val timestamp: Instant
) : TextMessageKey

@JsonTypeName("ChatMessage")
data class TestTextMessage(
        override val key: TestTextMessageKey,
        override val value: String,
        override val visible: Boolean
) : TextMessage

@JsonTypeName("JoinAlert")
data class TestJoinAlert(
        override val key: TestAlertMessageKey,
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
        registerSubtypes(TestTextMessage::class.java, TestJoinAlert::class.java)
    }.findAndRegisterModules()!!

    private fun randomMessage(): Message<Any, Any> {

        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        counter++

        return if (counter % 2 == 0)
            JoinAlert.create(
                    AlertMessageKey.create(messageId, roomId),  userId, true)
        else
            TextMessage.create(
                    TextMessageKey.create(messageId, roomId, userId),
                    "Hello $counter !",true)
    }

    @Test
    fun `Should serialize deserialize JSON from to Any Message`() {
        val messageJsons = ArrayList<String>()
        val messages = ArrayList<Message<TopicMessageKey, Any>>()

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
                            "ChatMessage" -> messages.add(mapper.readValue<TextMessage>(it))
                            "JoinAlert" -> messages.add(mapper.readValue<JoinAlert>(it))
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
                        is TextMessage -> Assertions.assertThat(msg.key).`as`("Has expected message state")
                                .isNotNull
                                .hasFieldOrProperty("id")
                        is JoinAlert -> Assertions.assertThat(msg.key).`as`("Has expected alert state")
                                .isNotNull
                                .hasFieldOrProperty("topicId")
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
                LeaveAlert.create(
                        AlertMessageKey.create(UUID.randomUUID(), UUID.randomUUID()),
                        UUID.randomUUID(), true),
                randomMessage(), randomMessage(), randomMessage())

        messages.forEach { msg ->

            when (msg) {
                is TextMessage -> Assertions.assertThat(msg.key).`as`("Has expected message state")
                        .isNotNull
                        .hasFieldOrProperty("id")
                is JoinAlert -> Assertions.assertThat(msg.key).`as`("Has expected alert state")
                        .isNotNull
                        .hasFieldOrProperty("topicId")
                else -> Assertions.assertThat(msg).`as`("Is a message, afterall")
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

        val key = AlertMessageKey.create(
                messageId,
                roomId
        )

        val value = RoomMetaData(
                1,
                2
        )

        val topicMessage: Message<AlertMessageKey, RoomMetaData> =
                InfoAlert.create(key, value, true)

        // Get some message and access specific fields
        if (topicMessage is InfoAlert) {
            Assertions.assertThat(topicMessage.key)
                    .`as`("infoAlert key is consistent state")
                    .isNotNull
                    .hasFieldOrPropertyWithValue("topicId", roomId)

            Assertions.assertThat(topicMessage.value)
                    .`as`("Message value is consistent state")
                    .isNotNull
                    .hasFieldOrPropertyWithValue("totalMessages", 2)
        } else {
            Assertions
                    .assertThat(topicMessage)
                    .isInstanceOf(InfoAlert::class.java)
        }
    }
}