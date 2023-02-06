package com.demo.chat.persistence.cassandra.domain.keygen

import com.demo.chat.service.core.IKeyGenerator
import java.util.concurrent.atomic.AtomicLong

class AtomicLongKeyGenerator(startSeed: Long) : IKeyGenerator<Long> {
    override fun nextKey(): Long = atom.incrementAndGet()

    private val atom = AtomicLong(startSeed)
}