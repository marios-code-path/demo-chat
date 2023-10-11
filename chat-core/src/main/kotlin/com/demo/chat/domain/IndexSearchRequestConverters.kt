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

    override fun membershipRequestToQuery(req: MembershipRequest<T>) =
            IndexSearchRequest(
                MembershipIndexService.MEMBER,
                "${req.uid.toString()} AND ${MembershipIndexService.MEMBEROF}:${req.roomId.toString()}",
                100
            )
}
//    topicIdQueryFunction = { req -> IndexSearchRequest(MessageIndexService.TOPIC, typeUtil.toString(req.id), 100) },
//    topicNameQueryFunction = { req -> IndexSearchRequest(TopicIndexService.NAME, req.name, 100) },
//    membershipOfIdQueryFunction = { req ->
//        IndexSearchRequest(
//            MembershipIndexService.MEMBEROF,
//            typeUtil.toString(req.id),
//            100
//        )
//    },
//    membershipRequestQueryFunction = { req ->
//        IndexSearchRequest(
//            MembershipIndexService.MEMBER,
//            "${typeUtil.toString(req.uid)} AND ${MembershipIndexService.MEMBEROF}:${typeUtil.toString(req.roomId)}",
//            100
//        )
//    },
//    handleQueryFunction = { req -> IndexSearchRequest(UserIndexService.HANDLE, req.name, 100) }