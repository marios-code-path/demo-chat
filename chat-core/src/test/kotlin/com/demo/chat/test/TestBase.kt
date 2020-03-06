package com.demo.chat.test

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.module.SimpleAbstractTypeResolver
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.util.*

open class TestBase {
    var counter = Random().nextInt()

    val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule()).apply {
        propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
        findAndRegisterModules()
    }!!
}

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
fun <T> module(name: String, iface: Class<T>, concrete: Class<out T>) = SimpleModule("CustomModel$name", Version.unknownVersion())
        .apply { setAbstractTypes(SimpleAbstractTypeResolver().apply { addMapping(iface, concrete) }) }