package com.demo.chat.service

import org.mockito.Mockito

// KLUDGE needed to get mockito to talk with Kotlin (type soup remember me?)
object TestBase

fun <T> anyObject(): T {
    Mockito.anyObject<T>()
    return uninitialized()
}

fun <T> uninitialized(): T = null as T