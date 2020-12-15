package com.demo.chat.deploy.config.codec

import com.demo.chat.ByHandleRequest
import com.demo.chat.ByIdRequest
import com.demo.chat.ByNameRequest

interface RequestToQueryConverters<T, Q> {
    fun topicNameToQuery(req: ByNameRequest): Q
    fun topicIdToQuery(req: ByIdRequest<T>): Q
    fun userHandleToQuery(req: ByHandleRequest): Q
    fun membershipIdToQuery(req: ByIdRequest<T>): Q
}