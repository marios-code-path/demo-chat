package com.demo.chat.test.memory

import com.demo.chat.domain.*
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.test.index.IndexTests
import java.util.function.Function
import java.util.function.Supplier

class TopicMembershipIndexTests : IndexTests<Long, TopicMembership<Long>, IndexSearchRequest>(
    LuceneIndex<Long, TopicMembership<Long>>(
        IndexEntryEncoder.ofTopicMembership(),
        { str -> Key.funKey(LongUtil().fromString(str)) },
        { t -> Key.funKey(t.key) }),
    Supplier { TopicMembership.create(123456L, 1234L, 12345L) },
    Function<TopicMembership<Long>, Key<Long>> { topicMembership -> Key.funKey(topicMembership.key) },
    Supplier { IndexSearchRequest(MembershipIndexService.MEMBER, LongUtil().toString(1234L), 1000) }
) {
    override fun getIndex(): IndexService<Long, TopicMembership<Long>, IndexSearchRequest> = myIndex
}

