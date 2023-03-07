package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import com.demo.chat.test.anyObject
import com.demo.chat.test.randomAlphaNumeric
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.mockito.Mockito
import org.mockito.kotlin.given
import reactor.core.publisher.Mono
import java.lang.reflect.ParameterizedType
import java.util.*
import java.util.concurrent.atomic.AtomicLong

class MockKeyServiceResolver : ParameterResolver {
    override fun supportsParameter(param: ParameterContext?, ext: ExtensionContext?): Boolean =
        with(param?.parameter?.parameterizedType!!) {
            val pt = this as ParameterizedType

            when (pt.rawType) {
                IKeyService::class.java -> true
                else -> false
            }
        }

    override fun resolveParameter(param: ParameterContext?, ext: ExtensionContext?): Any =
        with(param?.parameter?.parameterizedType!!) {
            val pt = this as ParameterizedType
            println(pt.actualTypeArguments[0])
            when (pt.actualTypeArguments[0]) {
                UUID::class.java -> testKey<UUID>()
                Number::class.java -> testKey<Long>()
                String::class.java -> testKey<String>()
                else -> Exception("No Provider for KeyService Parameter")
            }
        }

    val counter = AtomicLong(0)

    inline fun <reified T : Any> mock(): T = Mockito.mock(T::class.java)!!

    private inline fun <reified T> testKey(): IKeyService<T> = mock<IKeyService<T>>()
        .apply {
            given(this.exists(anyObject()))
                .willReturn(Mono.just(false))
            given(this.rem(anyObject()))
                .willReturn(Mono.empty())

            when (T::class) {
                UUID::class -> given(this.key<Any>(anyObject()))
                    .willReturn(Mono.just(Key.funKey(UUID.randomUUID() as T)))

                String::class -> given(this.key<Any>(anyObject()))
                    .willReturn(Mono.just(Key.funKey(randomAlphaNumeric(48) as T)))

                Number::class, Int::class, Long::class -> given(this.key<Any>(anyObject()))
                    .willReturn(Mono.just(Key.funKey(counter.incrementAndGet() as T)))
            }
        }
}