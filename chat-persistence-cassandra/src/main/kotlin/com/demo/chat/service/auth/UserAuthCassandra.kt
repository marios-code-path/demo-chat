package com.demo.chat.service.auth

import com.demo.chat.domain.Key
import com.demo.chat.domain.UsernamePasswordAuthenticationException
import com.demo.chat.service.*
import com.demo.chat.service.impl.memory.auth.AuthenticationServiceImpl
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.BiFunction
import java.util.function.Function

/**
 * Should do the chore of handling any authentication and authorization operations
 * using Cassandra components as the backing store
 */
class AuthorizationServiceCassandra<T, M>()