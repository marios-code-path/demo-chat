package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import com.demo.chat.service.impl.memory.index.InMemoryIndex
import com.demo.chat.service.impl.memory.index.IndexSearchRequest
import com.demo.chat.test.index.IndexTestBase
import java.util.function.Supplier

class MemoryIndexTests : IndexTestBase<Long, User<Long>, IndexSearchRequest>(
        Supplier { User.create(Key.funKey(1L), "test", "test1234", "localhost") },
        Supplier { Key.funKey(1L) },
        Supplier { IndexSearchRequest("name", "+test*", 1000) }
) {
    val index = InMemoryIndex<Long, User<Long>>(
            { t ->
                listOf(
                        Pair("handle", t.handle),
                        Pair("name", t.name)
                )
            }, { q -> Key.funKey(q.toLong()) })

    override fun getIndex(): IndexService<Long, User<Long>, IndexSearchRequest> = index
}