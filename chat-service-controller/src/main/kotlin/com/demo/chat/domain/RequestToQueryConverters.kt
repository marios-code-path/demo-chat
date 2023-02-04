package com.demo.chat.domain

interface RequestToQueryConverters<T, Q> {
    fun topicNameToQuery(req: ByNameRequest): Q
    fun topicIdToQuery(req: ByIdRequest<T>): Q
    fun userHandleToQuery(req: ByHandleRequest): Q
    fun membershipIdToQuery(req: ByIdRequest<T>): Q
}