package com.demo.chat.deploy.event

import com.demo.chat.domain.knownkey.RootKeys
import org.springframework.context.ApplicationEvent

class RootKeyInitializationReadyEvent<T>(val rootKeys: RootKeys<T>) : ApplicationEvent(rootKeys)