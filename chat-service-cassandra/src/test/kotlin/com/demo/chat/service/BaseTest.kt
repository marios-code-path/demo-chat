package com.demo.chat.service

import com.demo.chat.domain.ChatUser
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.mockito.Mockito

// KLUDGE needed to get mockito to talk with Kotlin (type soup remember me?)
object TestUtil

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