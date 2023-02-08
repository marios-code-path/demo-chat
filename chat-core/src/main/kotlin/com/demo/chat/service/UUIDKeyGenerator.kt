package com.demo.chat.service

import com.demo.chat.domain.SnowflakeGenerator
import com.demo.chat.service.core.IKeyGenerator
import java.util.*

class UUIDKeyGenerator(nodeId: Int) : IKeyGenerator<UUID> {
    private val idGenerator: IKeyGenerator<Long> = when (nodeId) {
        0 -> SnowflakeGenerator()
        else -> SnowflakeGenerator(nodeId)
    }

    override fun nextKey(): UUID =
        UUID.nameUUIDFromBytes(idGenerator.nextKey().toString().encodeToByteArray())

}