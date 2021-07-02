package com.demo.chat.secure

import com.demo.chat.domain.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.stream.Collectors


data class ChatUserDetails<T>(val user: User<T>, val roles: Collection<String>) : UserDetails {
    override fun getAuthorities(): MutableCollection<out GrantedAuthority> =
        roles.stream()
            .map { rb -> SimpleGrantedAuthority(rb) }
            .collect(Collectors.toList())

    override fun getPassword(): String = "" // LOL

    override fun getUsername(): String = user.handle

    override fun isAccountNonExpired(): Boolean = false

    override fun isAccountNonLocked(): Boolean = false

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = true
}