package com.demo.chat.index.lucene.impl

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.core.UserIndexService
import java.util.function.Function

open class UserLuceneIndex<T>(
    entityEncoder: Function<User<T>, List<Pair<String, String>>>,
    keyEncoder: Function<String, Key<T>>,
    keyReceiver: Function<User<T>, Key<T>>,
) : LuceneIndex<T, User<T>>(entityEncoder, keyEncoder, keyReceiver),
    UserIndexService<T, IndexSearchRequest>