package com.demo.chat.test.key

import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import java.util.*

class InMemoryKeyTests
    : KeyServiceTestBase(KeyServiceInMemory<Int> { Random(1024).nextInt() })