package com.demo.chat.test.route

import com.demo.chat.domain.Key
import com.demo.chat.test.route.Catalog.entry
import com.demo.chat.test.route.Catalog.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.util.UUID

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

    data class Level5<T>(val key: Key<T>)
    data class Level4<T>(val next: Level5<T>)
    data class Level3<T>(val next: Level4<T>)
    data class Level2<T>(val next: Level3<T>)
    data class Level1<T>(val next: Level2<T>)
    data class Concrete(val userId: Long, val topicId: UUID, val limit: Int, val name: String)
    data class Opaque(val payload: Any, val when_: java.time.Instant)
    data class Node(val label: String, val child: Node?)

    interface DepthRoutes<T> {
        @ProbeMapping
        @Suppress("UNUSED_PARAMETER")
        fun deep(request: Level1<T>) = Unit

        @ProbeMapping
        @Suppress("UNUSED_PARAMETER")
        fun mapped(byName: Map<String, Key<T>>, byId: Map<Key<T>, String>) = Unit

        @ProbeMapping
        @Suppress("UNUSED_PARAMETER")
        fun concrete(request: Concrete) = Unit

        @ProbeMapping
        @Suppress("UNUSED_PARAMETER")
        fun opaque(request: Opaque) = Unit

        @ProbeMapping
        @Suppress("UNUSED_PARAMETER")
        fun cycle(node: Node) = Unit

        @ProbeMapping
        @Suppress("UNUSED_PARAMETER")
        fun keys(ids: List<Key<T>>) = Unit

        @ProbeMapping
        @Suppress("UNUSED_PARAMETER")
        fun names(ids: List<String>) = Unit
    }

    class DepthController<T> : DepthRoutes<T>

    private fun depth(method: String) =
        HandlerDiscovery.all(listOf(DepthController::class.java), ProbeMapping::class.java) { _: Class<*>, m: Method -> m.name }
            .single { it.method == method }

    @Test
    fun `a key below five nested values is reported`() {
        assertThat(depth("deep").identityFields).containsExactly("request.next.next.next.next.key")
    }

    @Test
    fun `map keys and map values are reported`() {
        assertThat(depth("mapped").identityFields).containsExactlyInAnyOrder("byName{}", "byId{key}")
    }

    @Test
    fun `a concrete Long and a UUID are reported, and other scalars are not`() {
        assertThat(depth("concrete").identityFields).containsExactlyInAnyOrder("request.userId", "request.topicId")
    }

    @Test
    fun `an opaque value is reported for classification`() {
        assertThat(depth("opaque").identityFields).containsExactly("request.payload")
    }

    @Test
    fun `a cycle is reported rather than followed`() {
        assertThat(depth("cycle").identityFields).containsExactly("node.child")
    }

    @Test
    fun `a signature keeps its generic structure`() {
        assertThat(depth("keys").signature).isEqualTo("List<Key<T>>")
        assertThat(depth("names").signature).isEqualTo("List<String>")
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
