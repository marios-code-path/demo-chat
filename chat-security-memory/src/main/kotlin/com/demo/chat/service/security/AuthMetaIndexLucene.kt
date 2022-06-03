package com.demo.chat.service.security

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.service.security.AuthMetaIndex.Companion.ID
import com.demo.chat.service.security.AuthMetaIndex.Companion.PRINCIPAL
import com.demo.chat.service.security.AuthMetaIndex.Companion.TARGET
import com.demo.chat.service.impl.lucene.index.LuceneIndex
import java.util.function.Function

open class AuthMetaIndexLucene<T, P : String>(keyEncoder: Function<String, Key<T>>) :
    AuthMetaIndex<T, IndexSearchRequest, P>,
    LuceneIndex<T, AuthMetadata<T, P>>(
    { t ->
        listOf(
            Pair(PRINCIPAL, t.principal.id.toString()),
            Pair(TARGET, t.target.id.toString()),
            Pair(ID, t.key.id.toString())
        )
    },
    keyEncoder,
    { t -> t.key }
)