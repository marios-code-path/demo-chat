package com.demo.chat.test.route

import com.demo.chat.domain.Key
import com.demo.chat.test.route.Catalog.entry
import com.demo.chat.test.route.Catalog.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * The discovery and the comparison of the route verification guard. See
 * `CHAT-avduuqwp`, T4 step 7.
 */
class HandlerDiscoveryTests {

    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class ProbeMapping

    data class ProbeMember<T>(val uid: T, val name: String)
    data class ProbeRequest<T>(val members: List<ProbeMember<T>>)
    data class ProbeRecord<T>(val dest: T, val flag: Boolean)
    data class ProbeMessage<T>(val key: Key<T>, val record: ProbeRecord<T>)

    /** A generic interface, so the handler below is inherited. */
    interface ProbeRoutes<T> {
        @ProbeMapping
        @Suppress("UNUSED_PARAMETER")
        fun route(request: ProbeRequest<T>, keys: List<Key<T>>, message: ProbeMessage<T>, label: String) = Unit

        @ProbeMapping
        fun plain(): String = "none"
    }

    class ProbeController<T> : ProbeRoutes<T>

    private fun discovered() =
        HandlerDiscovery.all(listOf(ProbeController::class.java), ProbeMapping::class.java) { _: Class<*>, m: Method -> m.name }

    @Test
    fun `the discovery reaches nested values, collections and inherited handlers`() {
        val found = discovered().single { it.method == "route" }.identityFields

        assertThat(found).containsExactlyInAnyOrder("request.members[].uid", "keys[]", "message.key", "message.record.dest")
    }

    @Test
    fun `a handler with no key and no id has no identity field`() {
        assertThat(discovered().single { it.method == "plain" }.identityFields).isEmpty()
    }

    private val handler = DiscoveredHandler("Owner", "route", "route", "ByIdRequest", setOf("req.id"))

    @Test
    fun `a matching entry reports nothing`() {
        assertThat(RouteCatalog.compare(listOf(handler), listOf(entry("Owner", "route", "ByIdRequest", "req.id" to verify("USER")))))
            .isEmpty()
    }

    @Test
    fun `a handler with no entry fails`() {
        assertThat(RouteCatalog.compare(listOf(handler), listOf())).singleElement().asString().startsWith("no catalog entry")
    }

    @Test
    fun `an entry with no handler fails`() {
        assertThat(RouteCatalog.compare(listOf(), listOf(entry("Owner", "route", "ByIdRequest", "req.id" to verify("USER")))))
            .singleElement().asString().startsWith("no handler")
    }

    @Test
    fun `an unnamed field fails`() {
        assertThat(RouteCatalog.compare(listOf(handler), listOf(entry("Owner", "route", "ByIdRequest"))))
            .singleElement().asString().startsWith("unnamed field")
    }

    @Test
    fun `a changed signature fails`() {
        assertThat(RouteCatalog.compare(listOf(handler), listOf(entry("Owner", "route", "Key", "req.id" to verify("USER")))))
            .singleElement().asString().startsWith("signature changed")
    }

    @Test
    fun `a stale field fails`() {
        assertThat(
            RouteCatalog.compare(
                listOf(handler),
                listOf(entry("Owner", "route", "ByIdRequest", "req.id" to verify("USER"), "req.gone" to verify("USER")))
            )
        ).singleElement().asString().startsWith("stale field")
    }
}
