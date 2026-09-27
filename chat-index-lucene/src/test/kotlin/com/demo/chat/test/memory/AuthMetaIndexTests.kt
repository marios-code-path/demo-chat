package com.demo.chat.test.memory

import com.demo.chat.test.key.FakeKeyServices

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.test.key.TestKeys

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
        { str -> Key.of(LongUtil().fromString(str), ROOTS.of(ChatDomain.AUTH_METADATA).id) },
        { t -> t.key }),
    Supplier { AuthMetadata.create(TestKeys.key(1234L), TestKeys.key(1L), TestKeys.key(2L), "TESTROLE", Long.MAX_VALUE) },
    Function<AuthMetadata<Long>, Key<Long>> { msg -> msg.key },
    Supplier { IndexSearchRequest(AuthMetaIndex.PRINCIPAL, LongUtil().toString(1L), 1000) },
    ROOTS,
    ChatDomain.AUTH_METADATA,
) {
    override fun getIndex(): IndexService<Long, AuthMetadata<Long>, IndexSearchRequest> = myIndex
}

private val ROOTS = FakeKeyServices.longRoots()
