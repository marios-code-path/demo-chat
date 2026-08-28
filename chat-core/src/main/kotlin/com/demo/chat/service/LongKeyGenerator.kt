package com.demo.chat.service

import com.demo.chat.domain.SnowflakeGenerator
import com.demo.chat.service.core.IKeyGenerator

class LongKeyGenerator(nodeId: Int) : IKeyGenerator<Long> {
    private val idGenerator: IKeyGenerator<Long> = SnowflakeGenerator(nodeId)

    override fun nextId(): Long = idGenerator.nextId()
}
