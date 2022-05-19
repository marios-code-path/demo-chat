package com.demo.chat.test.stream

import com.demo.chat.codec.Decoder
import com.demo.chat.service.impl.memory.stream.ExampleReactiveStreamManager
import com.demo.chat.test.randomAlphaNumeric
import java.util.*

object UUIDKeyCodec : Decoder<Unit, UUID> {
    override fun decode(record: Unit): UUID = UUID.randomUUID()
}

object StringValCodec : Decoder<Unit, String> {
    override fun decode(record: Unit): String = randomAlphaNumeric(50)
}

/**
 * TODO Refactor into Integration PubSubChannel (see StreamManager)
 */
class ReactiveStreamManagerTestBase : StreamManagerTestBase<UUID, String>(ExampleReactiveStreamManager<UUID, String>(),
        UUIDKeyCodec,
        StringValCodec)