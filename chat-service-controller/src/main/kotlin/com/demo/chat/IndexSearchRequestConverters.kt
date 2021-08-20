package com.demo.chat

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.UserIndexService

class IndexSearchRequestConverters<T> : RequestToQueryConverters<T, IndexSearchRequest> {
    override fun topicNameToQuery(req: ByNameRequest) =
            IndexSearchRequest(TopicIndexService.NAME, req.name, 100)

    override fun topicIdToQuery(req: ByIdRequest<T>) =
            IndexSearchRequest(TopicIndexService.ID, req.id.toString(), 100)

    override fun userHandleToQuery(req: ByHandleRequest) =
            IndexSearchRequest(UserIndexService.HANDLE, req.handle, 100)

    override fun membershipIdToQuery(req: ByIdRequest<T>) =
            IndexSearchRequest(MembershipIndexService.MEMBEROF, req.id.toString(), 100)
}