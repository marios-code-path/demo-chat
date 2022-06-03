package com.demo.chat.secure

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.service.index.AuthMetadataIndex
import java.util.function.Function

object AuthPrincipleByKeySearch : Function<Key<Long>, IndexSearchRequest> {
    override fun apply(m: Key<Long>): IndexSearchRequest =
        IndexSearchRequest(AuthMetadataIndex.PRINCIPAL, m.id.toString(), 100)
}

object AuthTargetByKeySearch : Function<Key<Long>, IndexSearchRequest> {
    override fun apply(m: Key<Long>): IndexSearchRequest =
        IndexSearchRequest(AuthMetadataIndex.TARGET, m.id.toString(), 100)
}
