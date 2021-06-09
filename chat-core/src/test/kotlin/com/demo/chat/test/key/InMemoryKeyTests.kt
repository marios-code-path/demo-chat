package com.demo.chat.test.key

import com.demo.chat.service.IKeyService
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

class InMemoryKeyTests
    : KeyServiceTestBase<Number>(KeyServiceInMemory { Random(1024).nextInt() })

//@ExtendWith(MockKeyServiceResolver::class)
//class MockKeyTests(keyService : IKeyService<Number>)
//    : KeyServiceTestBase<Number>(keyService)