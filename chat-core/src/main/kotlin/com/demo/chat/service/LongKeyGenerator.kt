package com.demo.chat.service

import com.demo.chat.domain.SnowflakeGenerator
import com.demo.chat.service.core.IKeyGenerator

class LongKeyGenerator(nodeId: Int) : IKeyGenerator<Long> {
    private val idGenerator: IKeyGenerator<Long> = when (nodeId) {
        0 -> SnowflakeGenerator()
        else -> SnowflakeGenerator(nodeId)
    }

    override fun nextKey(): Long = idGenerator.nextKey()
}