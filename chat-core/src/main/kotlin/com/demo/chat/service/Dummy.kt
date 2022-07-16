package com.demo.chat.service

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.service.dummy.DummyIndexService
import com.demo.chat.service.dummy.DummyPersistenceStore
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.security.AuthMetaPersistence

class DummyAuthMetaPersistence<T> : DummyPersistenceStore<T, AuthMetadata<T>>(), AuthMetaPersistence<T>

class DummyAuthMetaIndex<T, Q> : DummyIndexService<T, AuthMetadata<T>, Q>(), AuthMetaIndex<T, Q>