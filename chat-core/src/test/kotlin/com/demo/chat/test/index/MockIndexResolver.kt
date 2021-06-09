package com.demo.chat.test.index

import com.demo.chat.service.IndexService
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.lang.reflect.ParameterizedType

class MockIndexResolver : ParameterResolver {
    override fun supportsParameter(param: ParameterContext?, ext: ExtensionContext?): Boolean =
            with(param?.parameter?.parameterizedType!!) {
                val pt = this as ParameterizedType

                when (pt.rawType) {
                    IndexService::class.java -> true
                    else -> false
                }
            }

    override fun resolveParameter(param: ParameterContext?, ext: ExtensionContext?): Any = supplier.get<Any, Any, Any>()

    val supplier = MockIndexSupplier()
}
