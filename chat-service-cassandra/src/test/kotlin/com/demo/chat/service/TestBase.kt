package com.demo.chat.service

import com.demo.chat.domain.*
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertAll
import org.mockito.Mockito

// KLUDGE needed to get mockito to talk with Kotlin (type soup remember me?)
object TestBase

fun <T> anyObject(): T {
    Mockito.anyObject<T>()
    return uninitialized()
}

fun <T> uninitialized(): T = null as T

fun userAssertions(user: ChatUser) {
    MatcherAssert
            .assertThat("A User has key and properties", user,
                    Matchers.allOf(
                            Matchers.notNullValue(),
                            Matchers.hasProperty("name", Matchers.not(Matchers.isEmptyOrNullString())),
                            Matchers.hasProperty("key",
                                    Matchers
                                            .allOf(
                                                    Matchers.notNullValue(),
                                                    Matchers.hasProperty("handle"),
                                                    Matchers.hasProperty("userId")
                                            )
                            )
                    ))
}


// helper function to verify user state
fun userStateAssertions(user: User<UserKey>, handle: String?, name: String?) {
    assertAll("User Assertion",
            { Assertions.assertNotNull(user) },
            { Assertions.assertNotNull(user.key.userId) },
            { Assertions.assertNotNull(user.key.handle) },
            { Assertions.assertEquals(handle, user.key.handle) },
            { Assertions.assertEquals(name, user.name) }
    )
}


fun chatMessageAssertion(msg: ChatMessage, someBody: String) {
    assertAll("message contents in tact",
            { Assertions.assertNotNull(msg) },
            { Assertions.assertNotNull(msg.key.id) },
            { Assertions.assertNotNull(msg.key.userId) },
            { Assertions.assertNotNull(msg.key.roomId) },
            { Assertions.assertNotNull(msg.key.timestamp) },
            { Assertions.assertNotNull(msg.value) },
            { Assertions.assertEquals(msg.value, someBody) },
            { Assertions.assertTrue(msg.visible) }
    )
}


fun roomAssertions(room: ChatRoom) {
    assertAll("room contents in tact",
            { Assertions.assertNotNull(room) },
            { Assertions.assertNotNull(room.key.roomId) },
            { Assertions.assertNotNull(room.key.name) },
            { Assertions.assertNotNull(room.timestamp) }
    )
}
