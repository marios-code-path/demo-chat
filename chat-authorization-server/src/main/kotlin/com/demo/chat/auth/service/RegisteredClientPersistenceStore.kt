package com.demo.chat.auth.service

import com.demo.chat.service.core.PersistenceStore
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient

interface RegisteredClientPersistenceStore<T> : PersistenceStore<T, RegisteredClient>