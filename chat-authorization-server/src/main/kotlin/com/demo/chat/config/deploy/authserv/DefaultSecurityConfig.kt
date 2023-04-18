package com.demo.chat.config.deploy.authserv

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.core.userdetails.User
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.session.HttpSessionEventPublisher

@Configuration
@EnableWebSecurity
class DefaultSecurityConfig {

    @Bean
    @Throws(Exception::class)
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorizeRequests ->
                authorizeRequests
                    .requestMatchers("/actuator/**").permitAll()
                    .anyRequest().authenticated()
            }
            .formLogin(Customizer.withDefaults())

        return http.build()
    }

    @Bean fun sessionRegistry() = SessionRegistryImpl()

    @Bean fun httpSessionEvenPublisher() = HttpSessionEventPublisher()

}
/**
 *     @Bean
 *     fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
 *         http
 *             .authenticationManager(UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService()))
 *             .csrf().disable()
 *             .formLogin().disable()
 *             .httpBasic()
 *             .and()
 *             .authorizeExchange()
 *             .pathMatchers("/actuator/health").permitAll()
 *             .pathMatchers("/actuator/**")
 *             .hasRole("ACTUATOR")
 *             .anyExchange()
 *             .hasRole("ACTUATOR")
 *
 *         return http.build()
 *     }
 *
 *     fun userDetailsService() = MapReactiveUserDetailsService(
 *         org.springframework.security.core.userdetails.User
 *             .withUsername(actuatorUser)
 *             .password(passwordEncoder().encode(actuatorPasswd))
 *             .roles("ACTUATOR")
 *             .build()
 *     )
 *
 *     @Bean
 *     fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
 */