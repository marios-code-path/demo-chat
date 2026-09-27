package com.demo.chat.test.resolve

import com.demo.chat.test.route.HandlerDiscovery
import com.demo.chat.test.route.RouteCatalog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.lang.reflect.Method

/**
 * Every RSocket handler matches its entry in [RSocketRouteCatalog]. See
 * `CHAT-avduuqwp`, T4 step 7.
 */
class RouteSignatureGuardTests {

    private fun route(owner: Class<*>, method: Method): String {
        val type = AnnotatedElementUtils.findMergedAnnotation(owner, MessageMapping::class.java)
        val own = AnnotatedElementUtils.findMergedAnnotation(method, MessageMapping::class.java)
        return listOfNotNull(type?.value?.firstOrNull(), own?.value?.firstOrNull()).joinToString(".")
    }

    private fun discovered() = HandlerDiscovery.all(
        RouteCatalog.classesAnnotated("com.demo.chat", Controller::class.java),
        MessageMapping::class.java,
        ::route,
    )

    // A scan that finds nothing would pass every comparison.
    @Test
    fun `the discovery finds the RSocket controllers`() {
        val classes = RouteCatalog.classesAnnotated("com.demo.chat", Controller::class.java)
        assertThat(classes).`as`(classes.joinToString { it.name }).isNotEmpty()
        assertThat(discovered()).hasSizeGreaterThan(40)
    }

    @Test
    fun `every RSocket handler matches its verification catalog entry`() {
        val problems = RouteCatalog.compare(discovered(), RSocketRouteCatalog.entries)
        assertThat(problems).`as`(problems.joinToString("\n")).isEmpty()
    }
}
