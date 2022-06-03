package com.demo.chat.test.stream

import com.demo.chat.convert.Converter
import com.demo.chat.service.impl.memory.stream.ExampleReactiveStreamManager
import com.demo.chat.test.randomAlphaNumeric
import java.util.*

object UUIDKeyCodec : Converter<Unit, UUID> {
    override fun convert(record: Unit): UUID = UUID.randomUUID()
}

object StringValCodec : Converter<Unit, String> {
    override fun convert(record: Unit): String = randomAlphaNumeric(50)
}

/**
 * TODO Refactor into Integration PubSubChannel (see StreamManager)
 */
class ReactiveStreamManagerTestBase : StreamManagerTestBase<UUID, String>(ExampleReactiveStreamManager<UUID, String>(),
        UUIDKeyCodec,
        StringValCodec
)