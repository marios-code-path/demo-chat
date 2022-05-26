package com.demo.chat.test.messaging

import com.demo.chat.convert.Converter
import java.util.*

class StringUUIDKeyConverter : Converter<String, UUID> {
    override fun convert(record: String): UUID {
        return UUID.fromString(record)
    }
}

class UUIDKeyStringConverter : Converter<UUID, String> {
    override fun convert(record: UUID): String {
        return record.toString()
    }
}