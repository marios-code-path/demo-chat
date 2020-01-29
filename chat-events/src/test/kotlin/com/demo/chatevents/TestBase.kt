package com.demo.chatevents

import java.util.*

fun testRoomId(): UUID = UUID.fromString("ecb2cb88-5dd1-44c3-b818-301000000000")

fun testUserId(): UUID = UUID.fromString("ecb2cb88-5dd1-44c3-b818-133730000000")

fun randomText() = "Text ${Random().nextLong()}"
