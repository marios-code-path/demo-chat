package com.demo.chat.service.security

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.service.PersistenceStore

interface AuthMetaPersistence<T> : PersistenceStore<T, AuthMetadata<T>>