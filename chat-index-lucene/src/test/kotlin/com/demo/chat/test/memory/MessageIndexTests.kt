package com.demo.chat.test.memory

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
        { str -> Key.funKey(LongUtil().fromString(str)) },
        { t -> t.key }),
    Supplier { Message.create(MessageKey.create(1234L, 1L, 2L), "Hello", true) },
    Function<Message<Long, String>, Key<Long>> { msg -> msg.key },
    Supplier { IndexSearchRequest(MessageIndexService.TOPIC, LongUtil().toString(2L), 1000) }
) {
    override fun getIndex(): IndexService<Long, Message<Long, String>, IndexSearchRequest> = myIndex
}

