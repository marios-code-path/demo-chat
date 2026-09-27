package com.demo.chat.test.controller.webflux.route

import com.demo.chat.test.route.HandlerDiscovery
import com.demo.chat.test.route.RouteCatalog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.reflect.Method

/**
 * Every REST handler matches its entry in [RestRouteCatalog]. See
 * `CHAT-avduuqwp`, T4 step 7.
 */
class RestRouteGuardTests {

    private fun route(owner: Class<*>, method: Method): String {
        val type = AnnotatedElementUtils.findMergedAnnotation(owner, RequestMapping::class.java)
        val own = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping::class.java)
        val verb = own?.method?.joinToString(",") ?: ""
        return "$verb ${type?.path?.firstOrNull() ?: ""}${own?.path?.firstOrNull() ?: ""}".trim()
    }

    private fun discovered() = HandlerDiscovery.all(
        RouteCatalog.classesAnnotated("com.demo.chat", RestController::class.java),
        RequestMapping::class.java,
        ::route,
    )

    // A scan that finds nothing would pass every comparison.
    @Test
    fun `the discovery finds the REST controllers`() {
        assertThat(discovered()).hasSizeGreaterThan(40)
    }

    @Test
    fun `every REST handler matches its verification catalog entry`() {
        val problems = RouteCatalog.compare(discovered(), RestRouteCatalog.entries)
        assertThat(problems).`as`(problems.joinToString("\n")).isEmpty()
    }
}
