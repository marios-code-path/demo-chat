package com.demo.chat.deploy.test

import com.demo.chat.config.deploy.event.DeploymentEventPublisher
import com.demo.chat.config.deploy.event.DeploymentEventListeners
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@Import(
    DeploymentEventPublisher::class, DeploymentEventListeners::class

)
class StartupListenerTests {

    @Test fun `test startup listener`() {

    }
}