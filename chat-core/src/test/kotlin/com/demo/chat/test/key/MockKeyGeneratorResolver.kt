package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.test.randomAlphaNumeric
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.mockito.BDDMockito.given
import org.mockito.kotlin.mock
import java.lang.reflect.ParameterizedType
import java.util.*
import java.util.concurrent.atomic.AtomicLong

class MockKeyGeneratorResolver : ParameterResolver {
    override fun supportsParameter(param: ParameterContext?, ext: ExtensionContext?): Boolean =
        with(param?.parameter?.parameterizedType!!) {
            val pt = this as ParameterizedType

            val ret = when (pt.rawType) {
                IKeyGenerator::class.java -> true
                else -> false
            }

            ret
        }

    override fun resolveParameter(param: ParameterContext?, ext: ExtensionContext?): Any =
        with(param?.parameter?.parameterizedType!!) {
            val pt = this as ParameterizedType
            val ret = when (pt.actualTypeArguments[0]) {
                UUID::class.java -> testKeyGen<UUID>()
                java.lang.Long::class.java -> testKeyGen<Long>()
                java.lang.String::class.java -> testKeyGen<String>()
                else -> Exception("No Provider for KeyService Parameter")
            }

            return ret
        }

    val counter = AtomicLong(0)

    private inline fun <reified T> testKeyGen(): Any {

        val mocked = when (T::class) {
            UUID::class -> mock<IKeyGenerator<UUID>>().apply {
                given(this.nextId())
                    .willReturn(UUID.randomUUID())
            }

            String::class -> mock<IKeyGenerator<String>>().apply {
                given(this.nextId())
                    .willReturn(randomAlphaNumeric(8))
            }

            Long::class -> mock<IKeyGenerator<Long>>().apply {
                given(this.nextId())
                    .willReturn(counter.incrementAndGet())
            }

            else -> throw Exception("No Provider for KeyService Parameter")
        }

        given(mocked.nextKey())
            .willReturn(Key.funKey(counter.incrementAndGet()))

        return mocked
    }
}