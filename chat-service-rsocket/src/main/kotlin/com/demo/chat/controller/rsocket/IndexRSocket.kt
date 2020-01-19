package com.demo.chat.controller.rsocket

import com.demo.chat.domain.*
import com.demo.chat.controller.service.IndexServiceController
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.UserIndexService
import org.springframework.stereotype.Controller
import java.util.*

@Controller
class UserIndexRSocket(t: UserIndexService<UUID>) : IndexServiceController<UUID, User<UUID>, Map<String, String>>(t)

@Controller
class MessageIndexRSocket(t: MessageIndexService<UUID>) : IndexServiceController<UUID, Message<UUID,out Any>, Map<String, UUID>>(t)

@Controller
class RoomIndexRSocket(t: TopicIndexService<UUID>) : IndexServiceController<UUID, MessageTopic<UUID>, Map<String, String>>(t)

@Controller
class MembershipIndexRSocket(t: MembershipIndexService<UUID>) : IndexServiceController<UUID, TopicMembership<UUID>, Map<String, UUID>>(t)