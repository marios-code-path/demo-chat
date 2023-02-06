package com.demo.chat.client.rsocket.clients.core.config

import com.demo.chat.client.rsocket.clients.core.SecretStoreClient
import org.springframework.messaging.rsocket.RSocketRequester


class SecretsClient<T>(prefix: String, requester: RSocketRequester) :
    SecretStoreClient<T>(prefix, requester)