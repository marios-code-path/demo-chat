package com.demo.chat.test.domain

import com.demo.chat.domain.NodeIdClaimProperties
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration

class NodeIdClaimPropertiesTests {

    private fun props(
        ttl: Duration? = null,
        renew: Duration? = null,
        margin: Duration? = null,
        timeout: Duration? = null
    ) = NodeIdClaimProperties(ttl, renew, margin, timeout)

    private fun failure(block: () -> Unit): String =
        Assertions.assertThrows(IllegalArgumentException::class.java) { block() }.message!!

    @Test
    fun `defaults are thirty ten five and five`() {
        val p = props()
        Assertions.assertEquals(Duration.ofSeconds(30), p.ttl)
        Assertions.assertEquals(Duration.ofSeconds(10), p.renewInterval)
        Assertions.assertEquals(Duration.ofSeconds(5), p.safetyMargin)
        Assertions.assertEquals(Duration.ofSeconds(5), p.operationTimeout)
    }

    @Test
    fun `the default close deadline is twenty five seconds`() {
        Assertions.assertEquals(Duration.ofSeconds(25), props().closeDeadline)
    }

    @Test
    fun `a ttl under one second fails and states the rule`() {
        val text = failure { props(ttl = Duration.ofMillis(500)) }
        Assertions.assertTrue(text.contains("app.nodeid.claim.ttl"), text)
        Assertions.assertTrue(text.contains("at least 1s"), text)
    }

    @Test
    fun `a fractional ttl fails and states the rule`() {
        val text = failure { props(ttl = Duration.ofMillis(1500)) }
        Assertions.assertTrue(text.contains("whole seconds"), text)
    }

    @Test
    fun `a renew interval over one third of the ttl fails and states the rule`() {
        val text = failure { props(ttl = Duration.ofSeconds(30), renew = Duration.ofSeconds(11)) }
        Assertions.assertTrue(text.contains("app.nodeid.claim.renew-interval"), text)
        Assertions.assertTrue(text.contains("ttl / 3"), text)
    }

    @Test
    fun `a renew interval of exactly one third of the ttl is accepted`() {
        Assertions.assertEquals(
            Duration.ofSeconds(10),
            props(ttl = Duration.ofSeconds(30), renew = Duration.ofSeconds(10)).renewInterval
        )
    }

    @Test
    fun `a safety margin at or over the ttl fails and states the rule`() {
        val text = failure { props(ttl = Duration.ofSeconds(30), margin = Duration.ofSeconds(30)) }
        Assertions.assertTrue(text.contains("app.nodeid.claim.safety-margin"), text)
    }

    @Test
    fun `an operation timeout equal to the renew interval fails and states the rule`() {
        val text = failure {
            props(renew = Duration.ofSeconds(10), timeout = Duration.ofSeconds(10))
        }
        Assertions.assertTrue(text.contains("app.nodeid.claim.operation-timeout"), text)
        Assertions.assertTrue(text.contains("less than"), text)
    }

    @Test
    fun `a close deadline at or under the renew interval fails and states the rule`() {
        val text = failure {
            props(
                ttl = Duration.ofSeconds(12),
                renew = Duration.ofSeconds(4),
                margin = Duration.ofSeconds(8),
                timeout = Duration.ofSeconds(1)
            )
        }
        Assertions.assertTrue(text.contains("greater than"), text)
        Assertions.assertTrue(text.contains("renew-interval"), text)
    }
}
