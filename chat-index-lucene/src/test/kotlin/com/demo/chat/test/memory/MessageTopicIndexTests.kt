package com.demo.chat.test.memory

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.MessageTopic
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.test.index.IndexTests
import java.util.function.Function
import java.util.function.Supplier

class MessageTopicIndexTests : IndexTests<Long, MessageTopic<Long>, IndexSearchRequest>(
    LuceneIndex<Long, MessageTopic<Long>>(
        IndexEntryEncoder.ofTopic<Long>(),
        { str -> Key.funKey(LongUtil().fromString(str)) },
        { t -> t.key }),
    Supplier { MessageTopic.create(Key.funKey(1234L), "TEST") },
    Function<MessageTopic<Long>, Key<Long>> { topic -> topic.key },
    Supplier { IndexSearchRequest(TopicIndexService.NAME, "TEST", 1000) }
) {
    override fun getIndex(): IndexService<Long, MessageTopic<Long>, IndexSearchRequest> = myIndex
}