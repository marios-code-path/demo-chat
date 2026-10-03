package com.demo.chat.test

import com.demo.chat.config.auth.AuthBeansConfiguration
import com.demo.chat.config.auth.RoomMemberGrantConfiguration
import com.demo.chat.config.auth.RoomOwnerGrantConfiguration
import com.demo.chat.config.auth.UserDetailsConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class CompositeAuthConditionTests {

    @ParameterizedTest
    @ValueSource(strings = ["", "false", "invalid"])
    fun `auth services and grant writers require an explicit true value`(value: String) {
        ApplicationContextRunner()
            .withUserConfiguration(
                AuthBeansConfiguration::class.java,
                RoomMemberGrantConfiguration::class.java,
                RoomOwnerGrantConfiguration::class.java,
                UserDetailsConfiguration::class.java,
            )
            .withPropertyValues("app.service.composite.auth=$value")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(AuthBeansConfiguration::class.java)
                assertThat(context).doesNotHaveBean(RoomMemberGrantConfiguration::class.java)
                assertThat(context).doesNotHaveBean(RoomOwnerGrantConfiguration::class.java)
                assertThat(context).doesNotHaveBean(UserDetailsConfiguration::class.java)
            }
    }
}
