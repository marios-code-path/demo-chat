package com.demo.chat.domain

import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.security.AuthMetaIndex

class IndexSearchRequestConverters : RequestToQueryConverters<IndexSearchRequest> {
    override fun topicNameToQuery(req: ByStringRequest) =
            IndexSearchRequest(TopicIndexService.NAME, req.name, 100)

    /**
     * Names the message destination field, not the topic id field.
     *
     * The one consumer is `MessagingServiceImpl.listenTopic`, and it reads the
     * message index. That index stores the destination of a message under
     * `MessageIndexService.TOPIC`. A query on the topic index field matched no
     * message document, so persisted room history never reached a caller.
     */
    override fun <T> topicIdToQuery(req: ByIdRequest<T>) =
            IndexSearchRequest(MessageIndexService.TOPIC, req.id.toString(), 100)

    override fun userHandleToQuery(req: ByStringRequest) =
            IndexSearchRequest(UserIndexService.HANDLE, req.name, 100)

    override fun <T> authPrincipalToQuery(req: ByIdRequest<T>) =
            IndexSearchRequest(AuthMetaIndex.PRINCIPAL, req.id.toString(), 100)

    override fun <T> authTargetToQuery(req: ByIdRequest<T>) =
            IndexSearchRequest(AuthMetaIndex.TARGET, req.id.toString(), 100)

    override fun <T> membershipIdToQuery(req: ByIdRequest<T>) =
            IndexSearchRequest(MembershipIndexService.MEMBEROF, req.id.toString(), 100)

    override fun <T> membershipRequestToQuery(req: MembershipRequest<T>) =
            IndexSearchRequest(
                MembershipIndexService.MEMBER,
                "${req.uid.toString()} AND ${MembershipIndexService.MEMBEROF}:${req.roomId.toString()}",
                100
            )

    /**
     * The value is a phrase. An analyzed uuid splits at each hyphen, and an
     * unquoted query would match every document that shares one segment.
     */
    override fun keyValueFieldToQuery(field: String, value: String) =
            IndexSearchRequest(field, "\"$value\"", 100)
}
