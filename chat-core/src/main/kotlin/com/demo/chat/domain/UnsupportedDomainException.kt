package com.demo.chat.domain

import com.demo.chat.domain.knownkey.ChatDomain

/**
 * A mint was refused for [name]. The name is a domain, or a type that has no
 * domain, such as `KeyCredential`. See `CHAT-avduuqwp`.
 */
class UnsupportedDomainException(val name: String) :
    ChatException("This key service does not mint keys in $name.") {
    constructor(domain: ChatDomain) : this(domain.wireName)
}
