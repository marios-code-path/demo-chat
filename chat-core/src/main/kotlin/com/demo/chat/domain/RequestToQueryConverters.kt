package com.demo.chat.domain

interface RequestToQueryConverters<Q> {
    fun topicNameToQuery(req: ByStringRequest): Q
    fun <T> topicIdToQuery(req: ByIdRequest<T>): Q
    fun userHandleToQuery(req: ByStringRequest): Q
    fun <T> membershipIdToQuery(req: ByIdRequest<T>): Q
    fun <T> membershipRequestToQuery(req: MembershipRequest<T>): Q
}