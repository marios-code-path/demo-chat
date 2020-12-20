package com.demo.chat.test

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import com.demo.chat.service.impl.lucene.index.InMemoryIndex
import com.demo.chat.test.index.IndexTests
import java.util.function.Supplier


class MemoryUserIndexTests : IndexTests<Long, User<Long>, IndexSearchRequest>(
        InMemoryIndex<Long, User<Long>>(
                { t ->
                    listOf(
                            Pair("handle", t.handle),
                            Pair("name", t.name)
                    )
                }, { q -> Key.funKey(q.toLong()) }, { t -> t.key} ),
        Supplier { User.create(Key.funKey(1L), "test", "test1234", "localhost") },
        Supplier { Key.funKey(1L) },
        Supplier { IndexSearchRequest("name", "+test*", 1000) }
) {
    override fun getIndex(): IndexService<Long, User<Long>, IndexSearchRequest> = myIndex
}