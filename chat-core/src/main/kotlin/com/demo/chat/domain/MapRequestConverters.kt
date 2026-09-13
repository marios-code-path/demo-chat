package com.demo.chat.domain

import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.security.AuthMetaIndex


class MapRequestConverters : RequestToQueryConverters<Map<String, String>> {
    override fun topicNameToQuery(req: ByStringRequest) = mapOf(
        Pair(TopicIndexService.NAME, req.name),
        Pair("SAMPLE_SIZE", "100")
    )

    /**
     * Names the message destination field, not the topic id field.
     *
     * The one consumer is `MessagingServiceImpl.listenTopic`, and it reads the
     * message index. The cassandra message index reads `query.keys.first()`, so
     * `MessageIndexService.TOPIC` must stay the first entry. A map that led with
     * another key fell to the empty branch, and persisted room history never
     * reached a caller.
     */
    override fun <T> topicIdToQuery(req: ByIdRequest<T>) = mapOf(
        Pair(MessageIndexService.TOPIC, req.id.toString()),
        Pair("SAMPLE_SIZE", "100")
    )

    override fun userHandleToQuery(req: ByStringRequest) = mapOf(
        Pair(UserIndexService.HANDLE, req.name)
    )

    override fun <T> authPrincipalToQuery(req: ByIdRequest<T>) = mapOf(
        Pair(AuthMetaIndex.PRINCIPAL, req.id.toString())
    )

    override fun <T> authTargetToQuery(req: ByIdRequest<T>) = mapOf(
        Pair(AuthMetaIndex.TARGET, req.id.toString())
    )

    override fun <T> membershipIdToQuery(req: ByIdRequest<T>) = mapOf(
        Pair(MembershipIndexService.MEMBEROF, req.id.toString())
    )

    override fun <T> membershipRequestToQuery(req: MembershipRequest<T>): Map<String, String> = mapOf(
        Pair(MembershipIndexService.MEMBER, req.uid.toString()),
        Pair(MembershipIndexService.MEMBEROF, req.roomId.toString())
    )
}
