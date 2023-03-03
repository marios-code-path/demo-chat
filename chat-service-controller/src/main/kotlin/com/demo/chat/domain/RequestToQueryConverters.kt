package com.demo.chat.domain

interface RequestToQueryConverters<T, Q> {
    fun topicNameToQuery(req: ByStringRequest): Q
    fun topicIdToQuery(req: ByIdRequest<T>): Q
    fun userHandleToQuery(req: ByStringRequest): Q
    fun membershipIdToQuery(req: ByIdRequest<T>): Q
}