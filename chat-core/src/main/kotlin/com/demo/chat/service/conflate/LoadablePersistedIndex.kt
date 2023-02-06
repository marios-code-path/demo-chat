package com.demo.chat.service.conflate

import com.demo.chat.service.core.IndexService
import com.demo.chat.service.LoadableService
import com.demo.chat.service.core.PersistenceStore

class LoadablePersistedIndex<T, E, Q>(
    val persistence: PersistenceStore<T, E>,
    val index: IndexService<T, E, Q>,
) : IndexService<T, E, Q> by index, LoadableService {
    override fun load() = persistence
                    .all()
                    .flatMap(::add)
                    .then()
}