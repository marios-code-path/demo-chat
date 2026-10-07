package com.demo.chat.index.lucene.impl

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.TypeUtil
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.service.core.KeyValueIndexService

/**
 * A key-value entry is mutable, so this index replaces the entry of a key.
 *
 * The base index replaces by key with one atomic writer call, and it reads
 * and checks the fields before that call. An unregistered value type and a
 * field that uses a reserved name both fail without changing the index. A job
 * that reached SUCCEEDED therefore never stays searchable as RUNNING.
 */
open class KeyValueLuceneIndex<T>(
    typeUtil: TypeUtil<T>,
    rootKeys: RootKeys<T>,
    entryEncoder: IndexEntryEncoder<KeyValuePair<T, Any>>
) : KeyValueIndexService<T, IndexSearchRequest>,
    LuceneIndex<T, KeyValuePair<T, Any>>(
        entryEncoder,
        keyEncoder = { str -> Key.of(typeUtil.fromString(str), rootKeys.of(ChatDomain.KEY_VALUE_PAIR).id) },
        keyReceiver = { t -> t.key }
    )
