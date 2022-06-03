package com.demo.chat.secure

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.service.security.AuthMetaIndex
import java.util.function.Function

object AuthMetaPrincipleByKeySearch : Function<Key<Long>, IndexSearchRequest> {
    override fun apply(m: Key<Long>): IndexSearchRequest =
        IndexSearchRequest(AuthMetaIndex.PRINCIPAL, m.id.toString(), 100)
}

object AuthMetaTargetByKeySearch : Function<Key<Long>, IndexSearchRequest> {
    override fun apply(m: Key<Long>): IndexSearchRequest =
        IndexSearchRequest(AuthMetaIndex.TARGET, m.id.toString(), 100)
}
