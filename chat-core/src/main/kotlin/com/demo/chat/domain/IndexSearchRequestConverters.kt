package com.demo.chat.domain

import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.UserIndexService

class IndexSearchRequestConverters<T> : RequestToQueryConverters<T, IndexSearchRequest> {
    override fun topicNameToQuery(req: ByStringRequest) =
            IndexSearchRequest(TopicIndexService.NAME, req.name, 100)

    override fun topicIdToQuery(req: ByIdRequest<T>) =
            IndexSearchRequest(TopicIndexService.ID, req.id.toString(), 100)

    override fun userHandleToQuery(req: ByStringRequest) =
            IndexSearchRequest(UserIndexService.HANDLE, req.name, 100)

    override fun membershipIdToQuery(req: ByIdRequest<T>) =
            IndexSearchRequest(MembershipIndexService.MEMBEROF, req.id.toString(), 100)
}