package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.test.randomAlphaNumeric
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.mockito.kotlin.mock
import java.util.*
import java.util.concurrent.atomic.AtomicLong

open class MockKeyGenerator {

    val counter = AtomicLong(0)

    inline fun <reified T> testKeyGen(): Any {

        val mocked = when (T::class) {
            UUID::class -> mock<IKeyGenerator<UUID>>().apply {
                BDDMockito.given(this.nextId())
                    .willReturn(UUID.randomUUID())
                BDDMockito.given(this.nextKey())
                    .willReturn(Key.funKey(UUID.randomUUID()))
            }

            String::class -> mock<IKeyGenerator<String>>().apply {
                BDDMockito.given(this.nextId())
                    .willReturn(randomAlphaNumeric(8))
                BDDMockito.given(this.nextKey())
                    .willReturn(Key.funKey(randomAlphaNumeric(8)))
            }

            Long::class -> mock<IKeyGenerator<Long>>().apply {
                Mockito.`when`(this.nextId())
                    .thenReturn(counter.incrementAndGet())
                val key: () -> Key<Long> = {Key.funKey(counter.incrementAndGet())}

                Mockito.`when`(this.nextKey())
                    .thenReturn(key())
//                BDDMockito.given(this.nextId())
  //                  .willReturn(counter.incrementAndGet())
    //            BDDMockito.given(this.nextKey())
      //              .willReturn(Key.funKey(this.nextId()))
            }

            else -> throw Exception("Known Key Generic for " + T::class)
        }


        return mocked
    }
}