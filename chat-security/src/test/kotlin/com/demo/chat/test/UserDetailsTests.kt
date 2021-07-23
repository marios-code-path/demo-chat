package com.demo.chat.test

import com.demo.chat.secure.service.ChatUserDetailsService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.security.core.userdetails.UserDetails
import reactor.test.StepVerifier
import java.util.function.Supplier

@Disabled
open class UserDetailsTests<T, E, Q>(
    private val svc: ChatUserDetailsService<T, E, Q>,
    private val uNameSupplier: Supplier<String>,
    private val userSupplier: Supplier<UserDetails>,
    private val pwSupplier: Supplier<String>
) {
    @Test
    fun `should findByUsername`() {
        StepVerifier
            .create(svc.findByUsername(uNameSupplier.get()))
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
            }
            .verifyComplete()
    }

    @Test
    fun `should update password`() {
        StepVerifier
            .create(svc.updatePassword(userSupplier.get(), pwSupplier.get()))
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
            }
            .verifyComplete()
    }
}