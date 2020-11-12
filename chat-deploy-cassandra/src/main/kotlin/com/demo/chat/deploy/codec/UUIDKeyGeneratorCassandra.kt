package com.demo.chat.deploy.codec

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.codec.Codec
import java.util.*

open class UUIDKeyGeneratorCassandra : Codec<Unit, UUID> {
    override fun decode(record: Unit): UUID {
        return UUIDs.timeBased()
    }
}