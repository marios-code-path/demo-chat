package com.demo.chat

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.UserMessageKey
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.ReplayProcessor
import java.time.Instant
import java.util.*
import java.util.stream.Stream


class MessageTests {

    private var counter = Random().nextInt()

    private val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule()).apply {
        propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
        findAndRegisterModules()
        //registerModules(
        //        module("AlertKey", MessageKey::class.java, TestAlertKey::class.java),
        //        module("TextKey", UserMessageKey::class.java, TestMessageKey::class.java))
    }!!

    @JsonTypeName("AlertKey")
    data class TestAlertKey(override val dest: UUID) : MessageKey<UUID, UUID> {
        override val id: UUID = UUID(0, 0)
        override val timestamp = Instant.now()
    }

    @JsonTypeName("Alert")
    data class TestAlert(override val key: TestAlertKey, override val data: Int) : Message<UUID, Int> {
        override val visible = false
    }

    @JsonTypeName("TextKey")
    data class TestMessageKey(override val id: UUID, override val dest: UUID, override val userId: UUID) : UserMessageKey<UUID, UUID, UUID> {
        override val timestamp = Instant.now()
    }

    @JsonTypeName("Text")
    data class TestTextMessage(override val key: TestMessageKey, override val data: String) : TextMessage<UUID> {
        override val visible = true
    }

    @Test
    fun `Should serialize deserialize JSON from to Any Message`() {
        val messageJsons = ArrayList<String>()
        val messages = ArrayList<Message<UUID, out Any>>()

        Stream.generate { randomMessage() }.limit(5)
                .forEach { msg ->
                    messageJsons.add(mapper.writeValueAsString(msg))
                }

        messageJsons
                .forEach {
                    val tree = mapper.readTree(it)

                    if (tree.fieldNames().hasNext()) {
                        when (tree.fieldNames().next()) {
                            "Text" -> messages.add(mapper.readValue<TestTextMessage>(it))
                            "Alert" -> messages.add(mapper.readValue<TestAlert>(it))
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
                        is TestAlert -> Assertions.assertThat(msg.key).`as`("Has expected alert state")
                                .isNotNull
                                .hasFieldOrProperty("dest")
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

    // use a replay processor to handle subscriber state lol
    //
    @Test
    fun `should test streaming only through publisher`() {
        val publisher = Flux.just(1)
        val subscribers: Flux<Disposable> = ReplayProcessor
                .create { sink ->
                    sink.next(
                            publisher.subscribe { myInt ->
                                sink.onRequest { myLong ->

                                }
                            }
                    )
                }

        publisher.subscribe {

        }
    }

    @Test
    fun `returns many message and smart casts`() {
        val messages = listOf(randomMessage(), randomMessage(),
                TestAlert(
                        TestAlertKey(UUID.randomUUID()),
                        kotlin.random.Random.nextInt()),
                randomMessage(), randomMessage(), randomMessage())

        messages.forEach { msg ->

            when (msg) {
                is TextMessage -> Assertions.assertThat(msg.key).`as`("Has expected message state")
                        .isNotNull
                        .hasFieldOrProperty("id")
                is TestAlert -> Assertions.assertThat(msg.key).`as`("Has expected alert state")
                        .isNotNull
                        .hasFieldOrProperty("dest")
                else -> Assertions.assertThat(msg).`as`("Is a message, afterall")
                        .isNotNull
                        .hasFieldOrProperty("key")
                        .hasFieldOrProperty("value")
            }
        }
    }

    class TestContextConfiguration {
        @Bean
        fun objectMapper() = ObjectMapper().registerModule(KotlinModule()).apply {
            propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
            findAndRegisterModules()
        }!!

    }

    private fun randomMessage(): Message<out Any, out Any> {

        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        counter++

        return if (counter % 2 == 0)
            TestAlert(TestAlertKey(roomId), counter)
        else
            TestTextMessage(TestMessageKey(messageId, roomId, userId), "Count: $counter")
    }
}