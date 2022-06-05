package com.demo.chat.service.security

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.service.IndexService

interface AuthMetaIndex<T, Q> : IndexService<T, AuthMetadata<T>, Q> {
    companion object {
        const val PRINCIPAL = "pid"
        const val TARGET = "tid"
        const val ID = "id"
    }
}