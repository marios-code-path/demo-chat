package com.demo.chat.domain

import com.demo.chat.domain.knownkey.ChatDomain

/** A key service refuses to mint in [domain]. See `CHAT-avduuqwp`. */
class UnsupportedDomainException(domain: ChatDomain) :
    ChatException("This key service does not mint keys in ${domain.wireName}.")
