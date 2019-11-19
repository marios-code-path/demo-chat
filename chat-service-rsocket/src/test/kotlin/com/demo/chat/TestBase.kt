package com.demo.chat

import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.module.SimpleAbstractTypeResolver
import com.fasterxml.jackson.databind.module.SimpleModule
import org.mockito.Mockito

fun <T> anyObject(): T {
    Mockito.anyObject<T>()
    return uninitialized()
}

fun <T> uninitialized(): T = null as T

class TestVoid()

private val ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
fun randomAlphaNumeric(size: Int): String {
    var count = size
    val builder = StringBuilder()
    while (count-- != 0) {
        val character = (Math.random() * ALPHA_NUMERIC_STRING.length).toInt()
        builder.append(ALPHA_NUMERIC_STRING[character])
    }
    return builder.toString()
}


// Register this abstract module to let the app know when it sees a Interface type, which
// concrete type to use on the way out.
fun <T> module(name: String, iface : Class<T>, concrete: Class<out T>) =  SimpleModule("CustomModel$name", Version.unknownVersion()).apply {

    val resolver = SimpleAbstractTypeResolver().apply {
        addMapping(iface, concrete)
    }
    setAbstractTypes(resolver)
}
