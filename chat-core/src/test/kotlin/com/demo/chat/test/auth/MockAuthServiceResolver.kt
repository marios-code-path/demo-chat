package com.demo.chat.test.auth

import com.demo.chat.service.AuthService
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.lang.reflect.ParameterizedType

class MockAuthServiceResolver : ParameterResolver {
    override fun supportsParameter(param: ParameterContext?, ext: ExtensionContext?): Boolean =
        with(param?.parameter?.parameterizedType!!) {
            val pt = this as ParameterizedType

            when (pt.rawType) {
                AuthService::class.java -> true
                else -> false
            }
        }

    // This can be fixed...
    override fun resolveParameter(param: ParameterContext?, ext: ExtensionContext?): Any = supplier.get<Any>()

    val supplier = MockAuthServiceSupplier()
}


