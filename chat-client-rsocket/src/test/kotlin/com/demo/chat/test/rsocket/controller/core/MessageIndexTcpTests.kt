package com.demo.chat.test.rsocket.controller.core

import com.demo.chat.test.rsocket.TestConfigurationTcpRSocketServer
import org.springframework.context.annotation.Import

@Import(TestConfigurationTcpRSocketServer::class)
class MessageIndexTcpTests : MessageIndexRSocketTests()