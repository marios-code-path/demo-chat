package com.demo.chat.service.client

interface ClientProperty {
    var dest: String?
    var prefix: String?

    companion object Factory {
        @JvmStatic
        fun create(d: String, p: String): ClientProperty = object : ClientProperty {
            override var dest: String? = ""
                get() = d
            override var prefix: String? = ""
                get() = p
        }
    }
}

interface CompositeRSocketClientProperties {
    val topic: ClientProperty
    val message: ClientProperty
    val user: ClientProperty
}

interface CoreRSocketClientProperties {
    val key: ClientProperty
    val index: ClientProperty
    val persistence: ClientProperty
    val pubsub: ClientProperty
    val secrets: ClientProperty
}
