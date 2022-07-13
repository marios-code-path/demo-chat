package com.demo.chat.service.impl.index.lucene

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.security.AuthMetaIndex.Companion.ID
import com.demo.chat.service.security.AuthMetaIndex.Companion.PRINCIPAL
import com.demo.chat.service.security.AuthMetaIndex.Companion.TARGET
import com.demo.chat.service.security.AuthMetaIndex

open class AuthMetaIndexLucene<T>(typeUtil: TypeUtil<T>) :
    AuthMetaIndex<T, IndexSearchRequest>,
    LuceneIndex<T, AuthMetadata<T>>(
        entityEncoder = { t ->
            listOf(
                Pair(PRINCIPAL, typeUtil.toString(t.principal.id)),
                Pair(TARGET, typeUtil.toString(t.target.id)),
                Pair(ID, typeUtil.toString(t.key.id))
            )
        },
        keyEncoder = { str -> Key.funKey(typeUtil.fromString(str)) },
        keyReceiver = { t -> t.key }
    )