package com.demo.chat.test.memory

import com.demo.chat.test.key.FakeKeyServices

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.core.IndexService
import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.test.index.IndexTests
import java.util.function.Function
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.random.Random


class UserIndexTests : IndexTests<Long, User<Long>, IndexSearchRequest>(
        LuceneIndex<Long, User<Long>>(
                { t ->
                    listOf(
                            Pair("handle", t.handle),
                            Pair("name", t.name)
                    )
                }, { q -> Key.of(q.toLong(), ROOTS.of(ChatDomain.USER).id) }, { t -> t.key} ),
        Supplier { User.create(TestKeys.key(abs(Random.nextLong())), "test", "test1234"+ abs(Random.nextInt()), "localhost") },
        Function<User<Long>, Key<Long>> { user -> user.key},
        Supplier { IndexSearchRequest("name", "+test*", 1000) },
    ROOTS,
    ChatDomain.USER,
) {
    override fun getIndex(): IndexService<Long, User<Long>, IndexSearchRequest> = myIndex
}

private val ROOTS = FakeKeyServices.longRoots()
