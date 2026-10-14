package com.demo.chat.security

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.IndexFieldKinds
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.security.AuthMetaIndex
import java.util.function.Function

class AuthMetadataPrincipleKeySearch<T>(private val typeUtil: TypeUtil<T>) : Function<Key<T>, IndexSearchRequest> {
    override fun apply(m: Key<T>): IndexSearchRequest =
        IndexFieldKinds.AUTH.query(AuthMetaIndex.PRINCIPAL, typeUtil.toString(m.id))
}

class AuthMetadataTargetKeySearch<T>(private val typeUtil: TypeUtil<T>) : Function<Key<T>, IndexSearchRequest> {
    override fun apply(m: Key<T>): IndexSearchRequest =
        IndexFieldKinds.AUTH.query(AuthMetaIndex.TARGET, typeUtil.toString(m.id))
}
