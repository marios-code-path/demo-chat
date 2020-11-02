package com.demo.chat.test.stream

import com.demo.chat.codec.Codec
import com.demo.chat.service.impl.stream.ReactiveStreamManager
import com.demo.chat.test.randomAlphaNumeric
import java.util.*

object UUIDKeyCodec : Codec<Unit, UUID> {
    override fun decode(record: Unit): UUID = UUID.randomUUID()
}

object StringValCodec : Codec<Unit, String> {
    override fun decode(record: Unit): String = randomAlphaNumeric(50)
}

class ReactiveStreamManagerTestBase : StreamManagerTestBase<UUID, String>(ReactiveStreamManager<UUID, String>(),
        UUIDKeyCodec,
        StringValCodec)