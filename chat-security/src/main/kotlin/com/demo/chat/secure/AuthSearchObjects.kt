package com.demo.chat.secure

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.security.AuthMetaIndex
import java.util.function.Function

class AuthMetadataPrincipleKeySearch<T>(private val typeUtil: TypeUtil<T>) : Function<Key<T>, IndexSearchRequest> {
    override fun apply(m: Key<T>): IndexSearchRequest =
        IndexSearchRequest(AuthMetaIndex.PRINCIPAL, typeUtil.toString(m.id), 100)
}

class AuthMetadataTargetKeySearch<T>(private val typeUtil: TypeUtil<T>) : Function<Key<T>, IndexSearchRequest> {
    override fun apply(m: Key<T>): IndexSearchRequest =
        IndexSearchRequest(AuthMetaIndex.TARGET, typeUtil.toString(m.id), 100)
}