package com.demo.chat.index.lucene.impl

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.service.MessageIndexService
import java.util.function.Function

open class MessageLuceneIndex<T>(
    entityEncoder: Function<Message<T, String>, List<Pair<String, String>>>,
    keyEncoder: Function<String, Key<T>>,
    keyReceiver: Function<Message<T, String>, Key<T>>,
) : LuceneIndex<T, Message<T, String>>(entityEncoder, keyEncoder, keyReceiver),
    MessageIndexService<T, String, IndexSearchRequest>