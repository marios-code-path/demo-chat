package com.demo.chat.domain

import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.UserIndexService

class IndexSearchRequestConverters : RequestToQueryConverters<IndexSearchRequest> {
    override fun topicNameToQuery(req: ByStringRequest) =
            IndexSearchRequest(TopicIndexService.NAME, req.name, 100)

    override fun <T> topicIdToQuery(req: ByIdRequest<T>) =
            IndexSearchRequest(TopicIndexService.ID, req.id.toString(), 100)

    override fun userHandleToQuery(req: ByStringRequest) =
            IndexSearchRequest(UserIndexService.HANDLE, req.name, 100)

    override fun <T> membershipIdToQuery(req: ByIdRequest<T>) =
            IndexSearchRequest(MembershipIndexService.MEMBEROF, req.id.toString(), 100)

    override fun <T> membershipRequestToQuery(req: MembershipRequest<T>) =
            IndexSearchRequest(
                MembershipIndexService.MEMBER,
                "${req.uid.toString()} AND ${MembershipIndexService.MEMBEROF}:${req.roomId.toString()}",
                100
            )
}