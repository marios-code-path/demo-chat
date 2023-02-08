package com.demo.chat

import com.demo.chat.authserv.ChatAuthorizationServerApplication
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
	classes = [ChatAuthorizationServerApplication::class],
	properties = [
		"management.endpoints.enabled-by-default=false",
		"app.service.core.key=long",
		"app.service.core.index","app.service.core.persistence","app.service.core.auth",
		"app.service.core.secrets",
		"spring.cloud.consul.enabled=false","spring.cloud.consul.config.enabled=false",
		"spring.cloud.config.enabled=false", "spring.cloud.config.import-check.enabled=false"])
@ActiveProfiles("local-store")
class AuthorizationServerDeployTests {

	@Test
	fun contextLoads() {
	}

}