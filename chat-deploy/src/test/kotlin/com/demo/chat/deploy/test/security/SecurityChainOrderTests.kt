package com.demo.chat.deploy.test.security

import com.demo.chat.config.WebFluxSecurity
import com.demo.chat.config.deploy.security.ActuatorWebSecurityConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.core.annotation.Order
import java.lang.reflect.Method

/**
 * The production chain methods declare the order, and the actuator chain wins.
 *
 * SecurityChainOwnershipTests builds a context, and its application chain
 * comes from a wrapper bean method. Spring reads @Order from the method that
 * declares the bean, and it does not carry the annotation over from a method
 * that the wrapper calls. So that suite cannot see the annotation on the
 * production method, and a change to it would pass every ownership test.
 *
 * This class reads the annotation on each production method instead.
 */
class SecurityChainOrderTests {

    @Test
    fun `each chain method declares an order`() {
        assertThat(orderOf(actuatorChainMethod()))
            .describedAs("the actuator chain method must declare @Order")
            .isNotNull()
        assertThat(orderOf(applicationChainMethod()))
            .describedAs("the application chain method must declare @Order")
            .isNotNull()
    }

    @Test
    fun `the actuator chain runs before the application chain`() {
        // The number is not the point. A lower value runs first, and the
        // actuator matcher is the narrow one.
        assertThat(orderOf(actuatorChainMethod()))
            .describedAs("the actuator chain must run first")
            .isLessThan(orderOf(applicationChainMethod()))
    }

    @Test
    fun `the context wires the application chain at the production order`() {
        // BothChainsApplication declares the wrapper method, and it must take
        // the production constant rather than a number of its own.
        val wrapper = ApplicationChainConfiguration::class.java.methods
            .single { it.name == "applicationFilterChain" }

        assertThat(orderOf(wrapper))
            .describedAs("the test wrapper must carry the production order")
            .isEqualTo(orderOf(applicationChainMethod()))
    }

    private fun actuatorChainMethod(): Method =
        ActuatorWebSecurityConfiguration::class.java.methods
            .single { it.name == "actuatorSecurityFilterChain" }

    private fun applicationChainMethod(): Method =
        WebFluxSecurity::class.java.methods.single { it.name == "filterChain" }

    private fun orderOf(method: Method): Int? =
        AnnotationUtils.findAnnotation(method, Order::class.java)?.value
}
