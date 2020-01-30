package com.demo.chat.controller.rsocket

import com.demo.chat.domain.*
import com.demo.chat.controller.service.IndexServiceController
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.UserIndexService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Controller
import java.util.*

@Profile("ctrl-index-user")
@Controller
class UserIndexRSocket(t: UserIndexService<UUID>) : IndexServiceController<UUID, User<UUID>, Map<String, String>>(t)

@Profile("ctrl-index-message")
@Controller
class MessageIndexRSocket(t: MessageIndexService<UUID, String>) : IndexServiceController<UUID, Message<UUID, String>, Map<String, UUID>>(t)

@Profile("ctrl-index-topic")
@Controller
class TopicIndexRSocket(t: TopicIndexService<UUID>) : IndexServiceController<UUID, MessageTopic<UUID>, Map<String, String>>(t)

@Profile("ctrl--index-membership")
@Controller
class MembershipIndexRSocket(t: MembershipIndexService<UUID>) : IndexServiceController<UUID, TopicMembership<UUID>, Map<String, UUID>>(t)