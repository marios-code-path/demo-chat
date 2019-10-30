package com.demo.chat.test.persistence

import com.demo.chat.service.ChatMembershipPersistence
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class MembershipPersistenceTests {
    lateinit var membershipSvc: ChatMembershipPersistence
}