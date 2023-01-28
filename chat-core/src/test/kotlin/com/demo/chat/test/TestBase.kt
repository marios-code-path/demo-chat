package com.demo.chat.test

import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertAll
import org.mockito.Mockito
import java.util.*

open class TestBase {
    var counter = Random().nextInt()

    val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule()).apply {
        propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
        findAndRegisterModules()
    }!!

    companion object TestBase {
        open fun <T> anyObject(): T {
            Mockito.any<T>()
            return uninitialized()
        }

        private fun <T> uninitialized(): T = null as T

        private val ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        open fun randomAlphaNumeric(size: Int): String {
            var count = size
            val builder = StringBuilder()
            while (count-- != 0) {
                val character = (Math.random() * ALPHA_NUMERIC_STRING.length).toInt()
                builder.append(ALPHA_NUMERIC_STRING[character])
            }
            return builder.toString()
        }

        fun topicAssertions(room: MessageTopic<UUID>) {
            assertAll("Topic Assertions",
                { Assertions.assertNotNull(room) },
                { Assertions.assertNotNull(room.key.id) },
                { Assertions.assertNotNull(room.data) })
        }

        fun userAssertions(user: User<UUID>, handle: String?, name: String?) {
            assertAll("User Assertions",
                { Assertions.assertNotNull(user) },
                { Assertions.assertNotNull(user.key.id) },
                { Assertions.assertNotNull(user.handle) },
                { Assertions.assertEquals(handle, user.handle) },
                { Assertions.assertEquals(name, user.name) }
            )
        }
    }
}

fun <T> anyObject(): T = TestBase.anyObject()

private fun <T> uninitialized(): T = null as T

private val ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

fun randomAlphaNumeric(size: Int): String = TestBase.randomAlphaNumeric(size)