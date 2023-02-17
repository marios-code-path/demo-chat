package com.demo.chat.index.lucene.impl

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.service.security.AuthMetaIndex.Companion.ID
import com.demo.chat.service.security.AuthMetaIndex.Companion.PRINCIPAL
import com.demo.chat.service.security.AuthMetaIndex.Companion.TARGET
import com.demo.chat.service.security.AuthMetaIndex

open class AuthMetaIndexLucene<T>(typeUtil: TypeUtil<T>) :
    AuthMetaIndex<T, IndexSearchRequest>,
    LuceneIndex<T, AuthMetadata<T>>(
        IndexEntryEncoder.ofAuthMeta(typeUtil),
        keyEncoder = { str -> Key.funKey(typeUtil.fromString(str)) },
        keyReceiver = { t -> t.key }
    )