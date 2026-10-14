package com.demo.chat.domain

import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.security.AuthMetaIndex

class IndexSearchRequestConverters : RequestToQueryConverters<IndexSearchRequest> {
    override fun topicNameToQuery(req: ByStringRequest) =
            IndexFieldKinds.TOPIC.query(TopicIndexService.NAME, req.name)

    /**
     * Names the message destination field, not the topic id field.
     *
     * The one consumer is `MessagingServiceImpl.listenTopic`, and it reads the
     * message index. That index stores the destination of a message under
     * `MessageIndexService.TOPIC`. A query on the topic index field matched no
     * message document, so persisted room history never reached a caller.
     */
    override fun <T> topicIdToQuery(req: ByIdRequest<T>) =
            IndexFieldKinds.MESSAGE.query(MessageIndexService.TOPIC, req.id.toString())

    override fun userHandleToQuery(req: ByStringRequest) =
            IndexFieldKinds.USER.query(UserIndexService.HANDLE, req.name)

    override fun <T> authPrincipalToQuery(req: ByIdRequest<T>) =
            IndexFieldKinds.AUTH.query(AuthMetaIndex.PRINCIPAL, req.id.toString())

    override fun <T> authTargetToQuery(req: ByIdRequest<T>) =
            IndexFieldKinds.AUTH.query(AuthMetaIndex.TARGET, req.id.toString())

    override fun <T> membershipIdToQuery(req: ByIdRequest<T>) =
            IndexFieldKinds.MEMBERSHIP.query(MembershipIndexService.MEMBEROF, req.id.toString())

    override fun <T> membershipRequestToQuery(req: MembershipRequest<T>) =
            IndexFieldKinds.MEMBERSHIP.query(
                MembershipIndexService.MEMBER_PAIR,
                MembershipIndexService.pairKeyOf(req.uid, req.roomId),
            )

    override fun keyValueFieldToQuery(field: String, value: String) =
            IndexFieldKinds.KEY_VALUE.query(field, value)
}
