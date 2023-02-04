package com.demo.chat.test

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.test.index.IndexTests
import java.util.function.Function
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.random.Random


class MemoryUserIndexTests : IndexTests<Long, User<Long>, IndexSearchRequest>(
        LuceneIndex<Long, User<Long>>(
                { t ->
                    listOf(
                            Pair("handle", t.handle),
                            Pair("name", t.name)
                    )
                }, { q -> Key.funKey(q.toLong()) }, { t -> t.key} ),
        Supplier { User.create(Key.funKey(abs(Random.nextLong())), "test", "test1234"+ abs(Random.nextInt()), "localhost") },
        Function<User<Long>, Key<Long>> { user -> user.key},
        Supplier { IndexSearchRequest("name", "+test*", 1000) }
) {
    override fun getIndex(): IndexService<Long, User<Long>, IndexSearchRequest> = myIndex
}