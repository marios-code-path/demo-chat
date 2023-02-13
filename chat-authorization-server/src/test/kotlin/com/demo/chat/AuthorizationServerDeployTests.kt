package com.demo.chat

import com.demo.chat.deploy.authserv.App
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.secure.service.ChatUserDetailsService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = [App::class],
	properties = [
		"management.endpoints.enabled-by-default=false",
		"app.service.core.key=long",
		"app.service.core.index","app.service.core.persistence","app.service.composite.auth",
		"app.service.core.secrets",
		"spring.cloud.consul.enabled=false","spring.cloud.consul.config.enabled=false",
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