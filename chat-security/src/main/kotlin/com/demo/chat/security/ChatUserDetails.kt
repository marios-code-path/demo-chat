package com.demo.chat.security

import com.demo.chat.domain.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.stream.Collectors

open class ChatUserDetails<T>(val user: User<T>, val roles: Collection<String>) : UserDetails {

    private var passwd: String? = null

    fun setPassword(password: String) {
        passwd = password
    }

    override fun getPassword(): String = passwd!!

    override fun getUsername(): String = user.handle

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = true

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> =
        roles.stream()
            .map { role -> SimpleGrantedAuthority(role) }
            .collect(Collectors.toList())
}