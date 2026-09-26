package com.demo.chat.test.memory

import com.demo.chat.test.key.FakeKeyServices

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.domain.Key

import com.demo.chat.test.key.TestKeys

import java.util.function.Function
import com.demo.chat.domain.*
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.test.index.IndexTests
import java.util.function.Supplier

class MessageIndexTests : IndexTests<Long, Message<Long, String>, IndexSearchRequest>(
    LuceneIndex<Long, Message<Long, String>>(
        IndexEntryEncoder.ofMessage(),
        { str -> Key.of(LongUtil().fromString(str), ROOTS.of(ChatDomain.MESSAGE).id) },
        { t -> t.key }),
    Supplier { Message.create(TestKeys.message(1234L, 1L, 2L), "Hello", true) },
    Function<Message<Long, String>, Key<Long>> { msg -> msg.key },
    Supplier { IndexSearchRequest(MessageIndexService.TOPIC, LongUtil().toString(2L), 1000) },
    ROOTS,
    ChatDomain.MESSAGE,
) {
    override fun getIndex(): IndexService<Long, Message<Long, String>, IndexSearchRequest> = myIndex
}

private val ROOTS = FakeKeyServices.longRoots()
