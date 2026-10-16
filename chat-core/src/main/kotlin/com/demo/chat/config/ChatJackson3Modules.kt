package com.demo.chat.config

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageImportRequest
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessagePointerRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.PointerMessage
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.domain.serializers.Jackson3AuthMetadataDeserializer
import com.demo.chat.domain.serializers.Jackson3ByIdRequestDeserializer
import com.demo.chat.domain.serializers.Jackson3KeyDeserializer
import com.demo.chat.domain.serializers.Jackson3KeyValuePairDeserializer
import com.demo.chat.domain.serializers.Jackson3MembershipDeserializer
import com.demo.chat.domain.serializers.Jackson3MembershipRequestDeserializer
import com.demo.chat.domain.serializers.Jackson3MessageDeserializer
import com.demo.chat.domain.serializers.Jackson3MessageImportRequestDeserializer
import com.demo.chat.domain.serializers.Jackson3MessageKeyDeserializer
import com.demo.chat.domain.serializers.Jackson3MessagePointerRequestDeserializer
import com.demo.chat.domain.serializers.Jackson3MessageSendRequestDeserializer
import com.demo.chat.domain.serializers.Jackson3MessageSubmitRequestDeserializer
import com.demo.chat.domain.serializers.Jackson3PointerMessageDeserializer
import com.demo.chat.domain.serializers.Jackson3TopicDeserializer
import com.demo.chat.domain.serializers.Jackson3UserDeserializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.JacksonModule
import tools.jackson.databind.module.SimpleModule

/**
 * The chat domain deserializers, as a Jackson 3 module.
 *
 * **This is the Jackson 3 half of the domain wire contract.**
 * `DefaultChatJacksonModules` publishes the Jackson 2 half, and both stay.
 * Jackson 2 serves the stores and the codecs of this repository, and Jackson 3
 * serves HTTP under Spring Boot 4.
 *
 * **A Jackson 3 codec cannot load a Jackson 2 module.** Without this module
 * the domain types reach the wire with no deserializer, and an index route
 * answers 500 with a type definition error. See CHAT-qwmjrixq.
 *
 * **`findAndAddModules` does not find this one.** That call reads the module
 * service registry, and this module is a Spring bean rather than a service
 * entry. Any codec that wants the domain types must add the bean.
 */
@Configuration
open class ChatJackson3Modules {

    @Bean
    open fun chatJackson3Module(): JacksonModule {
        val module = SimpleModule("ChatDomainJackson3")

        module.addDeserializer(Key::class.java, Jackson3KeyDeserializer<Any>())
        module.addDeserializer(MessageKey::class.java, Jackson3MessageKeyDeserializer<Any>())
        module.addDeserializer(Message::class.java, Jackson3MessageDeserializer<Any, Any>())
        module.addDeserializer(PointerMessage::class.java, Jackson3PointerMessageDeserializer<Any>())
        module.addDeserializer(KeyValuePair::class.java, Jackson3KeyValuePairDeserializer<Any, Any>())
        module.addDeserializer(User::class.java, Jackson3UserDeserializer<Any>())
        module.addDeserializer(TopicMembership::class.java, Jackson3MembershipDeserializer<Any>())
        module.addDeserializer(MessageTopic::class.java, Jackson3TopicDeserializer<Any>())
        module.addDeserializer(AuthMetadata::class.java, Jackson3AuthMetadataDeserializer<Any>())
        module.addDeserializer(ByIdRequest::class.java, Jackson3ByIdRequestDeserializer())
        module.addDeserializer(MessageImportRequest::class.java, Jackson3MessageImportRequestDeserializer())
        module.addDeserializer(MembershipRequest::class.java, Jackson3MembershipRequestDeserializer())
        module.addDeserializer(MessageSendRequest::class.java, Jackson3MessageSendRequestDeserializer())
        module.addDeserializer(MessageSubmitRequest::class.java, Jackson3MessageSubmitRequestDeserializer())
        module.addDeserializer(MessagePointerRequest::class.java, Jackson3MessagePointerRequestDeserializer())

        return module
    }
}
