package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.test.randomAlphaNumeric
import org.mockito.BDDMockito
import java.util.*
import java.util.concurrent.atomic.AtomicLong

inline fun <reified T : Any> mock(): T = BDDMockito.mock(T::class.java)!!

open class MockKeyGenerator {

    val counter = AtomicLong(0)

    inline fun <reified T> testKeyGen(): Any {

        val mocked = when (T::class) {
            UUID::class -> {
                val uuidMocked = mock<IKeyGenerator<UUID>>()
                BDDMockito.given(uuidMocked.nextId())
                    .willReturn(UUID.randomUUID())
                BDDMockito.given(uuidMocked.nextKey())
                    .willReturn(Key.funKey(UUID.randomUUID()))
                uuidMocked
            }

            String::class -> {
                val stringMocked = mock<IKeyGenerator<String>>()
                BDDMockito.given(stringMocked.nextId())
                    .willReturn(randomAlphaNumeric(8))
                BDDMockito.given(stringMocked.nextKey())
                    .willReturn(Key.funKey(randomAlphaNumeric(8)))
                stringMocked
            }

            Long::class -> {
                val longMocked = mock<IKeyGenerator<Long>>()
                BDDMockito.given(longMocked.nextId())
                    .willReturn(counter.incrementAndGet())
                BDDMockito.given(longMocked.nextKey())
                    .willReturn(Key.funKey(counter.incrementAndGet()))
                longMocked
            }

            else -> throw Exception("Unknown Key class for " + T::class)
        }

        return mocked
    }
}