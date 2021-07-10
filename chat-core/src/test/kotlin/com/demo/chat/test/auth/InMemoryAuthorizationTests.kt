package com.demo.chat.test.auth

import com.demo.chat.service.StringRoleAuthorizationMetadata
import com.demo.chat.service.impl.memory.auth.AuthorizationInMemory

class InMemoryAuthorizationTests : AuthorizationTests<StringRoleAuthorizationMetadata<Long>, Long>(
    AuthorizationInMemory(
        { m -> m.uid },
        { m -> m.target },
        { 0L },
        { m -> m.uid.toString() + m.target.toString() },
        { a, _ -> a }
    ),
    { StringRoleAuthorizationMetadata(1L, 1L, "TEST") },
    { 1L }
)