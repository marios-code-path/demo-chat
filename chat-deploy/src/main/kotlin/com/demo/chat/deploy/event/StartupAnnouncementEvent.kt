package com.demo.chat.deploy.event

import org.springframework.context.ApplicationEvent

class StartupAnnouncementEvent(val message: String): ApplicationEvent(message)