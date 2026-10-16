package com.demo.chat.domain

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.service.core.ProcessLocalIndex
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.stereotype.Controller
import org.springframework.util.ClassUtils
import reactor.core.publisher.Mono
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Duration

/** A closed gate refuses a store write. See `CHAT-lswjobhz`. */
class StoreWriteRefusedException :
    IllegalStateException("The store write gate is closed. This process is releasing its store service leases.")

/**
 * Counts the store writes of a leased process. See `CHAT-lswjobhz` and
 * `docs/SERVICE-LEASE.md`.
 *
 * [admit] starts the write at once and runs it to its end. A caller that
 * cancels does not stop the write, and it does not end the count. A cancel
 * does not prove that the store write stopped.
 */
class StoreWriteGate(val drainTimeout: Duration) {
    private val lock = Object()
    private var count = 0
    private var closed = false

    val active: Int get() = synchronized(lock) { count }

    fun <T : Any> admit(write: () -> Mono<T>): Mono<T> = Mono.deferContextual { context ->
        if (!enter()) return@deferContextual Mono.error<T>(StoreWriteRefusedException())
        // toFuture subscribes now, so the write runs to its end. That
        // subscription has no subscriber, so it carries the subscriber's
        // Context by hand. The cast drops the nullable type that toFuture
        // gives. Mono.fromFuture reads a null value as an empty Mono.
        @Suppress("UNCHECKED_CAST")
        val done = Mono.defer(write).contextWrite(context).doFinally { exit() }.toFuture()
            as java.util.concurrent.CompletableFuture<T>
        Mono.fromFuture(done, true)
    }

    /** Refuses new writes, and waits for the active ones. Returns false when the drain timeout passes first. */
    fun closeAndDrain(): Boolean = synchronized(lock) {
        closed = true
        val end = System.nanoTime() + drainTimeout.toNanos()
        while (count > 0) {
            val left = end - System.nanoTime()
            if (left <= 0) return false
            lock.wait(maxOf(1L, left / 1_000_000))
        }
        true
    }

    private fun enter(): Boolean = synchronized(lock) {
        if (closed) false else {
            count++
            true
        }
    }

    private fun exit() = synchronized(lock) {
        count--
        if (count == 0) lock.notifyAll()
    }
}

/**
 * Puts the store write gate around each store service. See `CHAT-lswjobhz`.
 *
 * A store service is what a `PersistenceServiceBeans` or `IndexServiceBeans`
 * holder returns. Two shapes reach this class:
 *
 * - A store service bean, which a `@Bean` method of a holder configuration
 *   returns. The proxy wraps the bean. A `@Controller` bean that implements a
 *   store interface is not a store service, and it is not wrapped.
 * - A plain holder bean, which is not a `@Configuration`. Its services are not
 *   beans, so the proxy wraps the holder, and the holder wraps each service
 *   that it returns. The Cassandra index holder has this shape.
 *
 * A `ProcessLocalIndex` is not wrapped. The proxy implements every interface
 * of its target, so an injection by interface still resolves.
 */
class StoreWriteGatePostProcessor(private val gate: ObjectProvider<StoreWriteGate>) : BeanPostProcessor, Ordered {

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        val type = ClassUtils.getUserClass(bean)
        return when {
            AnnotatedElementUtils.hasAnnotation(type, Controller::class.java) -> bean
            ServiceLeaseNames.isStoreService(bean) -> gated(bean, gate.getObject())
            isHolder(bean) && !AnnotatedElementUtils.hasAnnotation(type, Configuration::class.java) ->
                proxy(bean, GatedHolder(bean, gate.getObject()))
            else -> bean
        }
    }

    private class GatedWrites(val target: Any, private val gate: StoreWriteGate) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? = when {
            isObjectMethod(method) -> objectMethod(proxy, target, method, args)
            method.name in WRITES && Mono::class.java.isAssignableFrom(method.returnType) ->
                @Suppress("UNCHECKED_CAST")
                gate.admit { call(target, method, args) as Mono<Any> }
            else -> call(target, method, args)
        }
    }

    private class GatedHolder(private val target: Any, private val gate: StoreWriteGate) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? =
            if (isObjectMethod(method)) objectMethod(proxy, target, method, args)
            else call(target, method, args)?.let { gated(it, gate) }
    }

    companion object {
        /** The store service methods that write. Every other method passes through. */
        val WRITES = setOf("add", "rem", "addEnriched")

        /** True when [service] already writes through the gate. */
        fun isGated(service: Any): Boolean =
            Proxy.isProxyClass(service.javaClass) && Proxy.getInvocationHandler(service) is GatedWrites

        private fun isHolder(bean: Any): Boolean = bean is PersistenceServiceBeans<*, *> || bean is IndexServiceBeans<*, *, *>

        private fun gated(service: Any, gate: StoreWriteGate): Any =
            if (!ServiceLeaseNames.isStoreService(service) || service is ProcessLocalIndex || isGated(service)) service
            else proxy(service, GatedWrites(service, gate))

        private fun proxy(target: Any, handler: InvocationHandler): Any {
            val type = target.javaClass
            return Proxy.newProxyInstance(type.classLoader, ClassUtils.getAllInterfacesForClass(type, type.classLoader), handler)
        }

        private fun isObjectMethod(method: Method): Boolean =
            (method.name == "equals" && method.parameterCount == 1) ||
                (method.name == "hashCode" && method.parameterCount == 0) ||
                (method.name == "toString" && method.parameterCount == 0)

        private fun objectMethod(proxy: Any, target: Any, method: Method, args: Array<out Any?>?): Any = when (method.name) {
            "equals" -> proxy === args!![0]
            "hashCode" -> System.identityHashCode(proxy)
            else -> "gated $target"
        }

        private fun call(target: Any, method: Method, args: Array<out Any?>?): Any? =
            try {
                method.invoke(target, *(args ?: emptyArray()))
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
    }
}

/** Registers the gate and its post processor. The backend lease configurations import it. */
@Configuration(proxyBeanMethods = false)
class StoreWriteGateConfiguration {

    @Bean
    fun storeWriteGate(@Value("\${app.service.lease.drain-timeout:10s}") drainTimeout: Duration): StoreWriteGate =
        StoreWriteGate(drainTimeout)

    companion object {
        // A static bean method keeps the post processor out of the early
        // creation of this configuration.
        @Bean
        @JvmStatic
        fun storeWriteGatePostProcessor(gate: ObjectProvider<StoreWriteGate>): StoreWriteGatePostProcessor =
            StoreWriteGatePostProcessor(gate)
    }
}
