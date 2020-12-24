package com.demo.chat.deploy.app

import com.demo.chat.controller.core.IndexServiceController
import com.demo.chat.controller.core.PersistenceServiceController
import com.demo.chat.controller.edge.JoinAlert
import com.demo.chat.controller.edge.LeaveAlert
import com.demo.chat.deploy.config.codec.RequestToQueryConverters
import com.demo.chat.deploy.config.codec.ValueLiterals
import com.demo.chat.deploy.config.controllers.edge.ExchangeControllerConfig
import com.demo.chat.deploy.config.controllers.edge.TopicControllerConfiguration
import com.demo.chat.deploy.config.controllers.edge.UserControllerConfiguration
import com.demo.chat.deploy.config.core.IndexServiceConfiguration
import com.demo.chat.deploy.config.core.PersistenceServiceConfiguration
import com.demo.chat.domain.*
import com.demo.chat.service.PubSubService
import com.demo.chat.service.conflate.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.*


@ConditionalOnProperty(prefix = "app.service.edge", name = ["user"])
@Controller
@MessageMapping("edge.chat.user")
class UserConflation(
        persistenceConfig: PersistenceServiceConfiguration<UUID, String>,
        indexConfig: IndexServiceConfiguration<UUID, String, IndexSearchRequest>,
) : IndexServiceController<UUID, User<UUID>, IndexSearchRequest>(
        PersistedIndex(
                KeyFirstPersistence(persistenceConfig.user()) { ent, key ->
                    User.create(key, ent.name, ent.handle, ent.imageUri)
                },
                indexConfig.userIndex())
)

@ConditionalOnProperty(prefix = "app.service.edge", name = ["topic"])
@Controller
@MessageMapping("edge.chat.topic")
class TopicConflation(
        persistenceConfig: PersistenceServiceConfiguration<UUID, String>,
        indexConfig: IndexServiceConfiguration<UUID, String, IndexSearchRequest>,
) : IndexServiceController<UUID, MessageTopic<UUID>, IndexSearchRequest>(
        PersistedIndex(
                KeyFirstPersistence(persistenceConfig.topic()) { ent, key ->
                    MessageTopic.create(key, ent.data)
                },
                indexConfig.topicIndex())
)

@ConditionalOnProperty(prefix = "app.service.edge", name = ["membership"])
@Controller
@MessageMapping("edge.chat.membership")
class MembershipConflation(
        persistenceConfig: PersistenceServiceConfiguration<UUID, String>,
        indexConfig: IndexServiceConfiguration<UUID, String, IndexSearchRequest>,
        pubsub: PubSubService<UUID, String>,
        values: ValueLiterals<String>,
) : PersistenceServiceController<UUID, TopicMembership<UUID>>(
        PubSubbedPersistence(
                PublishConfiguration.create(
                        {
                            Optional.of(JoinAlert(
                                    MessageKey.create(
                                            it.key,
                                            it.member,
                                            it.memberOf
                                    ),
                                    values.emptyValue()))
                        }, {
                    Optional.of(LeaveAlert(
                            MessageKey.create(
                                    it.key, it.member, it.memberOf),
                            values.emptyValue()))
                }),
                IndexedPersistence(
                        KeyFirstPersistence(persistenceConfig.membership()) { ent, key ->
                            TopicMembership.create(
                                    key.id, ent.member, ent.memberOf
                            )
                        }, indexConfig.membershipIndex()),
                pubsub
        )
)

@ConditionalOnProperty(prefix = "app.legacy.edge", name = ["chat"])
@Controller
@MessageMapping("edge.chat.message")
class MessageConflation(
        persistenceConfig: PersistenceServiceConfiguration<UUID, String>,
        indexConfig: IndexServiceConfiguration<UUID, String, IndexSearchRequest>,
        pubsub: PubSubService<UUID, String>,
) : PersistenceServiceController<UUID, Message<UUID, String>>(
        PubSubbedPersistence(
                PublishConfiguration.create({
                    Optional.of(it)
                }, {
                    Optional.of(Message.create(it.key,it.data,false)) // TODO implement MessageDeletion(messageKey)
                }),
                IndexedPersistence(
                        KeyFirstPersistence(persistenceConfig.message()
                        ) { ent, key ->
                            Message
                                    .create(MessageKey.create(
                                            key.id, ent.key.from, ent.key.dest
                                    ), ent.data, ent.record)
                        },
                        indexConfig.messageIndex()),
                pubsub
        )
)

@ConditionalOnProperty(prefix = "app.legacy.edge", name = ["messaging"])
@Controller
@MessageMapping("edge.message")
class ExchangeController(
        indexConfig: IndexServiceConfiguration<UUID, String, IndexSearchRequest>,
        persistenceConfig: PersistenceServiceConfiguration<UUID, String>,
        pubsub: PubSubService<UUID, String>,
        reqs: RequestToQueryConverters<UUID, IndexSearchRequest>,
) :
        ExchangeControllerConfig<UUID, String, IndexSearchRequest>(
                indexConfig,
                persistenceConfig, pubsub, reqs)

@ConditionalOnProperty(prefix = "app.legacy.edge", name = ["user"])
@Controller
@MessageMapping("edge.user")
class UserController(
        persistenceConfig: PersistenceServiceConfiguration<UUID, String>,
        indexConfig: IndexServiceConfiguration<UUID, String, IndexSearchRequest>,
        reqs: RequestToQueryConverters<UUID, IndexSearchRequest>,
) : UserControllerConfiguration<UUID, String, IndexSearchRequest>(persistenceConfig, indexConfig, reqs)

@ConditionalOnProperty(prefix = "app.legacy.edge", name = ["topic"])
@Controller
@MessageMapping("edge.topic")
class TopicController(
        persistenceConfig: PersistenceServiceConfiguration<UUID, String>,
        indexConfig: IndexServiceConfiguration<UUID, String, IndexSearchRequest>,
        pubsub: PubSubService<UUID, String>,
        valueCodecs: ValueLiterals<String>,
        reqs: RequestToQueryConverters<UUID, IndexSearchRequest>,
) :
        TopicControllerConfiguration<UUID, String, IndexSearchRequest>(
                persistenceConfig,
                indexConfig,
                pubsub,
                valueCodecs,
                reqs
        )