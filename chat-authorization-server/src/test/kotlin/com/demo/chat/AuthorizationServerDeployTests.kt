package com.demo.chat

import com.demo.chat.deploy.authserv.AuthServiceApp
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.secure.service.ChatUserDetailsService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = [AuthServiceApp::class],
	properties = [
		"management.endpoints.enabled-by-default=false",
		"app.service.core.key", "app.key.type=long",
		"app.service.core.index","app.service.core.persistence","app.service.composite.auth",
		"app.service.core.secrets", "app.composite.service.auth", "app.composite.service.user",// add clients
		"spring.cloud.consul.enabled=false","spring.cloud.consul.config.enabled=false",
		"spring.cloud.consul.discovery.enabled=false","spring.cloud.discovery.enabled=false",
		"spring.cloud.config.enabled=false", "spring.cloud.config.import-check.enabled=false"])
class AuthorizationServerDeployTests {

	@Autowired
	private lateinit var typeUtil: TypeUtil<Long>

	@Autowired
	private lateinit var chatUserDetailsService: ChatUserDetailsService<Long, IndexSearchRequest>

	@Test
	fun contextLoads() {
	}

}