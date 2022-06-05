package com.demo.chat.client.rsocket.config

interface RSocketProperty {
    var dest: String?
    var prefix: String?

    companion object Factory {
        @JvmStatic
        fun create(d: String, p: String): RSocketProperty = object : RSocketProperty {
            override var dest: String? = ""
                get() = d
            override var prefix: String? = ""
                get() = p
        }
    }
}

interface EdgeRSocketProperties {
    val topic: RSocketProperty
    val message: RSocketProperty
    val user: RSocketProperty
}

interface CoreRSocketProperties {
    val key: RSocketProperty
    val index: RSocketProperty
    val persistence: RSocketProperty
    val pubsub: RSocketProperty
}
