package com.demo.chat.controller.core

import com.demo.chat.controller.core.mapping.TopicPubSubServiceMapping
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.TopicPubSubService

open class TopicPubSubServiceController<T : Any, V>(
    private val that: TopicPubSubService<T, V>,
    private val verifier: KeyVerifier<T>,
    private val typeUtil: TypeUtil<T>,
) : TopicPubSubServiceMapping<T, V>, TopicPubSubService<T, V> by that {
    override fun verifier(): KeyVerifier<T> = verifier
    override fun typeUtil(): TypeUtil<T> = typeUtil
}
