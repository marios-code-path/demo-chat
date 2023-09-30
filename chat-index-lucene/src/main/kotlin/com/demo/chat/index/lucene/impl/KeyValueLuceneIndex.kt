package com.demo.chat.index.lucene.impl

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.TypeUtil
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.service.core.KeyValueIndexService

open class KeyValueLuceneIndex<T>(
    typeUtil: TypeUtil<T>,
    entryEncoder: IndexEntryEncoder<KeyValuePair<T, Any>>
) : KeyValueIndexService<T, IndexSearchRequest>,
    LuceneIndex<T, KeyValuePair<T, Any>>(
        entryEncoder,
        keyEncoder = { str -> Key.funKey(typeUtil.fromString(str)) },
        keyReceiver = { t -> t.key }
    )