package com.demo.chat.service.impl.index.lucene

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.TopicIndexService
import java.util.function.Function

open class TopicLuceneIndex<T>(
    entityEncoder: Function<MessageTopic<T>, List<Pair<String, String>>>,
    keyEncoder: Function<String, Key<T>>,
    keyReceiver: Function<MessageTopic<T>, Key<T>>,
) : LuceneIndex<T, MessageTopic<T>>(entityEncoder, keyEncoder, keyReceiver),
    TopicIndexService<T, IndexSearchRequest>