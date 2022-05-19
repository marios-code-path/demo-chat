package com.demo.chat.test.messaging

import com.demo.chat.convert.Encoder
import java.util.*

class StringUUIDKeyEncoder : Encoder<String, UUID> {
    override fun encode(record: String): UUID {
        return UUID.fromString(record)
    }
}

class UUIDKeyStringEncoder : Encoder<UUID, String> {
    override fun encode(record: UUID): String {
        return record.toString()
    }
}