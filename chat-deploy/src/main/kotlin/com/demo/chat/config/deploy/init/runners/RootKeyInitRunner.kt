package com.demo.chat.config.deploy.init.runners

import com.demo.chat.config.deploy.event.DeploymentEventPublisher
import com.demo.chat.deploy.event.RootKeyInitializationReadyEvent
import com.demo.chat.deploy.event.StartupAnnouncementEvent
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.domain.knownkey.RootKeysSupplier
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner


//@Component
//@ConditionalOnProperty("app.rootkeys.create", havingValue = "true")
//@Order(10)
class RootKeyInitRunner<T>(
    val publisher: DeploymentEventPublisher,
    val rootKeys: RootKeys<T>,
    val rootKeyGen: RootKeysSupplier<T>
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        rootKeys.merge(rootKeyGen.get())
        publisher.publishEvent(StartupAnnouncementEvent("Root Keys Initialized"))
        publisher.publishEvent(RootKeyInitializationReadyEvent(rootKeys))

    }
}