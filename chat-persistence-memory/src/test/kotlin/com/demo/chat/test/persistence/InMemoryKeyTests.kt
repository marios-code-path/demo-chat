package com.demo.chat.test.persistence

import com.demo.chat.persistence.memory.impl.KeyServiceInMemory
import com.demo.chat.test.key.TestKeyServiceBase
import java.util.*

class InMemoryKeyTests
    : TestKeyServiceBase<Number>(KeyServiceInMemory { Random(1024).nextInt() })

//@ExtendWith(MockKeyServiceResolver::class)
//class MockKeyTests(keyService : IKeyService<Number>)
//    : KeyServiceTestBase<Number>(keyService)