package com.demo.chat.test.key

import com.demo.chat.service.core.IKeyGenerator
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.lang.reflect.ParameterizedType
import java.util.*

class MockKeyGeneratorResolver : ParameterResolver, MockKeyGenerator() {
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
}