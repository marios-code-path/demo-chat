package com.demo.chat.rsocket.test.client

import com.demo.chat.test.service.ServiceTestBase
import com.demo.chat.test.service.TestConfigurationRSocket
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestConfigurationRSocket::class)
class IndexTests : ServiceTestBase() {

}