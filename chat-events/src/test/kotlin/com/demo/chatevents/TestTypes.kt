package com.demo.chatevents

import com.demo.chat.domain.*
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*

object TestTypes


data class Usr(val name: String, val handle: String, val date: Date)

@JsonTypeName("TestEntity")
data class TestEntity(var data: String) {
    constructor() : this("foo")//Usr("","", Date()))
}