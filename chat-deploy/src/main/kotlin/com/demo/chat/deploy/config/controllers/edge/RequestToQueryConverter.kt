package com.demo.chat.deploy.config.controllers.edge

import com.demo.chat.ByHandleRequest
import com.demo.chat.ByIdRequest
import com.demo.chat.ByNameRequest
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.UserIndexService
import org.springframework.context.annotation.Bean
import java.util.*
import java.util.function.Function

interface RequestToQueryConverter<T, Q> {
    fun topicNameToQuery(req: ByNameRequest): Q
    fun topicIdToQuery(req: ByIdRequest<T>): Q
    fun userHandleToQuery(req: ByHandleRequest): Q
    fun membershipIdToQuery(req: ByIdRequest<T>): Q
}

class IndexSearchRequestConverters<T> : RequestToQueryConverter<T, IndexSearchRequest> {
    override fun topicNameToQuery(req: ByNameRequest) =
            IndexSearchRequest(TopicIndexService.NAME, req.name, 100)

    override fun topicIdToQuery(req: ByIdRequest<T>) =
             IndexSearchRequest(TopicIndexService.ID, req.id.toString(), 100)

    override fun userHandleToQuery(req: ByHandleRequest) =
            IndexSearchRequest(UserIndexService.HANDLE, req.handle, 100)

    override fun membershipIdToQuery(req: ByIdRequest<T>) =
            IndexSearchRequest(MembershipIndexService.MEMBEROF, req.id.toString(), 100)
}