package com.demo.chat.test.rsocket.controller.core

import com.demo.chat.test.rsocket.TestConfigurationWebSocketRSocketServer
import org.springframework.context.annotation.Import

@Import(TestConfigurationWebSocketRSocketServer::class)
class MessageIndexWSTests : MessageIndexRequesterTests()