package com.demo.chat.test.persistence

import com.demo.chat.service.conflate.KeyEnricherPersistenceStore
import com.demo.chat.service.core.EnricherPersistenceStore
import com.demo.chat.service.core.PersistenceStore
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.lang.reflect.ParameterizedType

class MockPersistenceResolver : ParameterResolver {
    var isEnriched = false;
    override fun supportsParameter(param: ParameterContext?, ext: ExtensionContext?): Boolean =
        with(param?.parameter?.parameterizedType!!) {
            val pt = this as ParameterizedType

            when (pt.rawType) {
                KeyEnricherPersistenceStore::class.java -> {
                    isEnriched = true; true
                }

                PersistenceStore::class.java -> true
                else -> false
            }
        }

    // This can be fixed...
    override fun resolveParameter(param: ParameterContext?, ext: ExtensionContext?): Any =
        when(isEnriched) {
            true -> MockEnricherPersistenceStoreSupplier().get<Any, Any, Any>()
            else -> MockPersistenceSupplier().get<Any, Any>()
        }

}