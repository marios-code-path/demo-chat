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
	//	"spring.config.location=classpath:application.yml",
		"app.service.core.key", "app.service.security.userdetails",
		"app.service.core.index","app.service.core.persistence","app.service.composite.auth",
		"app.service.core.secrets", "app.composite.service.auth", "app.composite.service.user",// add clients
		])
class AuthorizationServerDeployTests {

	@Autowired
	private lateinit var typeUtil: TypeUtil<Long>

	@Autowired
	private lateinit var chatUserDetailsService: ChatUserDetailsService<Long, IndexSearchRequest>

	@Test
	fun contextLoads() {
 	}
}