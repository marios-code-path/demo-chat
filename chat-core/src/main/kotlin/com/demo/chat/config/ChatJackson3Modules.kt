package com.demo.chat.config

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.domain.serializers.Jackson3AuthMetadataDeserializer
import com.demo.chat.domain.serializers.Jackson3KeyDeserializer
import com.demo.chat.domain.serializers.Jackson3KeyValuePairDeserializer
import com.demo.chat.domain.serializers.Jackson3MembershipDeserializer
import com.demo.chat.domain.serializers.Jackson3MessageDeserializer
import com.demo.chat.domain.serializers.Jackson3MessageKeyDeserializer
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
        module.addDeserializer(KeyValuePair::class.java, Jackson3KeyValuePairDeserializer<Any, Any>())
        module.addDeserializer(User::class.java, Jackson3UserDeserializer<Any>())
        module.addDeserializer(TopicMembership::class.java, Jackson3MembershipDeserializer<Any>())
        module.addDeserializer(MessageTopic::class.java, Jackson3TopicDeserializer<Any>())
        module.addDeserializer(AuthMetadata::class.java, Jackson3AuthMetadataDeserializer<Any>())

        return module
    }
}
