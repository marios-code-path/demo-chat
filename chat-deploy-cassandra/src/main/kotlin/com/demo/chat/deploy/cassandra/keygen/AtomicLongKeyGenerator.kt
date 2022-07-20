package com.demo.chat.deploy.cassandra.keygen

import com.demo.chat.service.IKeyGenerator
import java.util.concurrent.atomic.AtomicLong

class AtomicLongKeyGenerator(startSeed: Long) : IKeyGenerator<Long> {
    override fun nextKey(): Long = atom.incrementAndGet()

    private val atom = AtomicLong(startSeed)
}