package com.demo.chat.secure.service

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.secure.service.AuthIndex.Companion.ID
import com.demo.chat.secure.service.AuthIndex.Companion.PRINCIPAL
import com.demo.chat.secure.service.AuthIndex.Companion.TARGET
import com.demo.chat.service.AuthMetadata
import com.demo.chat.service.impl.lucene.index.LuceneIndex
import com.demo.chat.service.impl.memory.persistence.InMemoryPersistence
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import java.util.function.Function

object IncrementingKeyService : KeyServiceInMemory<Long>({ System.nanoTime() })

object AuthorizationPersistenceInMemory :
    InMemoryPersistence<Long, AuthMetadata<Long, String>>(
        IncrementingKeyService, AuthMetadata::class.java, { t -> t.key })

object AuthorizationMetaIndexInMemory : LuceneIndex<Long, AuthMetadata<Long, String>>(
    { t ->
        listOf(
            Pair(PRINCIPAL, t.principal.id.toString()),
            Pair(TARGET, t.target.id.toString()),
            Pair(ID, t.key.id.toString())
        )
    },
    { q -> Key.funKey(q.toLong()) },
    { t -> t.key }
)

object AuthPrincipleByKeySearch : Function<Key<Long>, IndexSearchRequest> {
    override fun apply(m: Key<Long>): IndexSearchRequest =
        IndexSearchRequest(PRINCIPAL, m.id.toString(), 100)
}

object AuthTargetByKeySearch : Function<Key<Long>, IndexSearchRequest> {
    override fun apply(m: Key<Long>): IndexSearchRequest =
        IndexSearchRequest(TARGET, m.id.toString(), 100)
}

class AuthIndex {
    companion object {
        const val PRINCIPAL = "pid"
        const val TARGET = "tid"
        const val ID = "id"
    }
}