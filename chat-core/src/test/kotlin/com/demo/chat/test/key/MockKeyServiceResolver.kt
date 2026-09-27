package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
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
    override fun supportsParameter(param: ParameterContext, ext: ExtensionContext): Boolean =
        with(param.parameter.parameterizedType) {
            val pt = this as ParameterizedType

            when (pt.rawType) {
                IKeyService::class.java -> true
                else -> false
            }
        }

    override fun resolveParameter(param: ParameterContext, ext: ExtensionContext): Any? =
        with(param.parameter.parameterizedType) {
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

    /**
     * A mock key service. It mints under a fixed root for each domain, from
     * `TestRoots`, and it answers that root from `rootOf`. See `CHAT-avduuqwp`.
     */
    private inline fun <reified T> testKey(): IKeyService<T> = mock<IKeyService<T>>()
        .apply {
            given(this.exists(anyObject()))
                .willReturn(Mono.just(false))
            given(this.rem(anyObject()))
                .willReturn(Mono.empty())

            val type: Class<*> = when (T::class) {
                UUID::class -> UUID::class.java
                String::class -> String::class.java
                else -> Long::class.java
            }
            val next: () -> Any = when (T::class) {
                UUID::class -> { -> UUID.randomUUID() }
                String::class -> { -> randomAlphaNumeric(48) }
                else -> { -> counter.incrementAndGet() }
            }
            given(this.key(anyObject())).willAnswer { call ->
                val domain = call.getArgument<ChatDomain>(0)
                @Suppress("UNCHECKED_CAST")
                Mono.just(Key.of(next() as T, TestRoots.of<T>(type, domain)))
            }
        }
}
