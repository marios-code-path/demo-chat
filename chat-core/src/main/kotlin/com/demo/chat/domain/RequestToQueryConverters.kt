package com.demo.chat.domain

interface RequestToQueryConverters<Q> {
    fun topicNameToQuery(req: ByStringRequest): Q
    fun <T> topicIdToQuery(req: ByIdRequest<T>): Q
    fun userHandleToQuery(req: ByStringRequest): Q
    fun <T> authPrincipalToQuery(req: ByIdRequest<T>): Q
    fun <T> authTargetToQuery(req: ByIdRequest<T>): Q
    fun <T> membershipIdToQuery(req: ByIdRequest<T>): Q
    fun <T> membershipRequestToQuery(req: MembershipRequest<T>): Q

    /** An exact match of [value] on the key-value index [field]. See `CHAT-avduuqwp`, D3. */
    fun keyValueFieldToQuery(field: String, value: String): Q
}
