package com.demo.chat.service.index

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.service.IndexService

interface AuthMetadataIndex<T, Q, P: String> : IndexService<T, AuthMetadata<T, P>, Q> {
    companion object {
        const val PRINCIPAL = "pid"
        const val TARGET = "tid"
        const val ID = "id"
    }
}