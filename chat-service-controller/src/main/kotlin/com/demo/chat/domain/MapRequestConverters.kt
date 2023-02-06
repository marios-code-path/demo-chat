package com.demo.chat.domain

import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.UserIndexService


class MapRequestConverters<T> : RequestToQueryConverters<T, Map<String, String>> {
    override fun topicNameToQuery(req: ByNameRequest) = mapOf(
            Pair(TopicIndexService.NAME, req.name),
            Pair("SAMPLE_SIZE", "100")
    )

    override fun topicIdToQuery(req: ByIdRequest<T>) = mapOf(
            Pair(TopicIndexService.ID, req.id.toString()),
            Pair("SAMPLE_SIZE", "100")
    )

    override fun userHandleToQuery(req: ByHandleRequest) = mapOf(
            Pair(UserIndexService.HANDLE, req.handle)
    )

    override fun membershipIdToQuery(req: ByIdRequest<T>) = mapOf(
            Pair(MembershipIndexService.MEMBEROF, req.id.toString())
    )
}