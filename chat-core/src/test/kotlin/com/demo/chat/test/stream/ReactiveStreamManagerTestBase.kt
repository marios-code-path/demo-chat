package com.demo.chat.test.stream

import com.demo.chat.convert.Encoder
import com.demo.chat.service.impl.memory.stream.ExampleReactiveStreamManager
import com.demo.chat.test.randomAlphaNumeric
import java.util.*

object UUIDKeyCodec : Encoder<Unit, UUID> {
    override fun encode(record: Unit): UUID = UUID.randomUUID()
}

object StringValCodec : Encoder<Unit, String> {
    override fun encode(record: Unit): String = randomAlphaNumeric(50)
}

/**
 * TODO Refactor into Integration PubSubChannel (see StreamManager)
 */
class ReactiveStreamManagerTestBase : StreamManagerTestBase<UUID, String>(ExampleReactiveStreamManager<UUID, String>(),
        UUIDKeyCodec,
        StringValCodec)