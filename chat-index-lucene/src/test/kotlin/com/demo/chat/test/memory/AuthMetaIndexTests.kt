package com.demo.chat.test.memory

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.test.index.IndexTests
import java.util.function.Supplier
import java.util.function.Function

class AuthMetaIndexTests : IndexTests<Long, AuthMetadata<Long>, IndexSearchRequest>(
    LuceneIndex<Long, AuthMetadata<Long>>(
        IndexEntryEncoder.ofAuthMeta(LongUtil()),
        { str -> Key.funKey(LongUtil().fromString(str)) },
        { t -> t.key }),
    Supplier { AuthMetadata.create(Key.funKey(1234L), Key.funKey(1L), Key.funKey(2L), "TESTROLE", Long.MAX_VALUE) },
    Function<AuthMetadata<Long>, Key<Long>> { msg -> msg.key },
    Supplier { IndexSearchRequest(AuthMetaIndex.PRINCIPAL, LongUtil().toString(1L), 1000) }
) {
    override fun getIndex(): IndexService<Long, AuthMetadata<Long>, IndexSearchRequest> = myIndex
}