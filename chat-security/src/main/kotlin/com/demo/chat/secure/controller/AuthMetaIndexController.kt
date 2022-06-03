package com.demo.chat.secure.controller

import com.demo.chat.controller.core.mapping.IndexServiceMapping
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.IndexService

open class AuthMetaIndexController<T, P: String>(private val that: AuthMetaIndex<T, IndexSearchRequest, P>) :
    IndexServiceMapping<T, AuthMetadata<T, P>, IndexSearchRequest>,
    IndexService<T, AuthMetadata<T, P>, IndexSearchRequest> by that