package com.demo.chat.test.persistence

import com.demo.chat.persistence.memory.impl.KeyServiceInMemory
import com.demo.chat.test.key.FakeKeyServices
import com.demo.chat.test.key.TestKeyServiceBase
import java.util.concurrent.atomic.AtomicLong

private val roots = FakeKeyServices.longRoots()
private val ids = AtomicLong()

class InMemoryKeyTests
    : TestKeyServiceBase<Long>(KeyServiceInMemory({ ids.incrementAndGet() }, roots), roots)
