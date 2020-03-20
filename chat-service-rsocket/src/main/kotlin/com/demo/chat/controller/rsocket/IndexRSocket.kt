package com.demo.chat.controller.rsocket

import com.demo.chat.controller.service.IndexServiceController
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.UserIndexService
import java.util.*

class UserIndexRSocket(t: UserIndexService<UUID>) : IndexServiceController<UUID, User<UUID>, Map<String, String>>(t)

class MessageIndexRSocket(t: MessageIndexService<UUID, String>) : IndexServiceController<UUID, Message<UUID, String>, Map<String, UUID>>(t)

class TopicIndexRSocket(t: TopicIndexService<UUID>) : IndexServiceController<UUID, MessageTopic<UUID>, Map<String, String>>(t)

class MembershipIndexRSocket(t: MembershipIndexService<UUID>) : IndexServiceController<UUID, TopicMembership<UUID>, Map<String, UUID>>(t)
