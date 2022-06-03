package com.demo.chat.test.persistence

import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import com.demo.chat.test.key.KeyServiceTestBase
import java.util.*

class InMemoryKeyTests
    : KeyServiceTestBase<Number>(KeyServiceInMemory { Random(1024).nextInt() })

//@ExtendWith(MockKeyServiceResolver::class)
//class MockKeyTests(keyService : IKeyService<Number>)
//    : KeyServiceTestBase<Number>(keyService)