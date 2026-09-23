package com.demo.chat.test.clock

import com.demo.chat.domain.NodeId
import com.demo.chat.domain.clock.ClockStamp
import com.demo.chat.domain.clock.NodeClock
import com.demo.chat.domain.clock.VectorClock
import com.demo.chat.domain.clock.VectorClock.Relation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The clock that orders grants.
 *
 * `key.id` carried the order until 2026-09-23. `SnowflakeGenerator` builds an
 * id from a wall clock, so that order moves when a host clock moves, and a
 * uuid key carries no order at all.
 */
class VectorClockTests {

    @Test
    fun `a tick raises the count of one node only`() {
        val clock = VectorClock().tick(NODE_1).tick(NODE_1).tick(NODE_2)

        assertThat(clock.countOf(NODE_1)).isEqualTo(2L)
        assertThat(clock.countOf(NODE_2)).isEqualTo(1L)
        assertThat(clock.countOf(NODE_3)).isEqualTo(0L)
    }

    @Test
    fun `a clock that a tick produced follows the clock before it`() {
        val before = VectorClock().tick(NODE_1)
        val after = before.tick(NODE_1)

        assertThat(before.relate(after)).isEqualTo(Relation.BEFORE)
        assertThat(after.relate(before)).isEqualTo(Relation.AFTER)
    }

    @Test
    fun `two clocks with the same counts are equal`() {
        assertThat(VectorClock().tick(NODE_1).relate(VectorClock().tick(NODE_1)))
            .isEqualTo(Relation.EQUAL)
    }

    /**
     * **This is the case a vector clock exists to report.** Two nodes each
     * wrote without seeing the other. Neither caused the other.
     */
    @Test
    fun `two nodes that did not see each other are concurrent`() {
        val left = VectorClock().tick(NODE_1)
        val right = VectorClock().tick(NODE_2)

        assertThat(left.relate(right)).isEqualTo(Relation.CONCURRENT)
        assertThat(right.relate(left)).isEqualTo(Relation.CONCURRENT)
    }

    @Test
    fun `a merge holds the higher count of every node`() {
        val left = VectorClock().tick(NODE_1).tick(NODE_1)
        val right = VectorClock().tick(NODE_1).tick(NODE_2)

        val merged = left.merge(right)

        assertThat(merged.countOf(NODE_1)).isEqualTo(2L)
        assertThat(merged.countOf(NODE_2)).isEqualTo(1L)
    }

    /**
     * A node that read another node, then wrote, follows that other node.
     * This is the path that makes an order meaningful across nodes.
     */
    @Test
    fun `a node that observes another then ticks follows it`() {
        val remote = VectorClock().tick(NODE_2)
        val clock = NodeClock(NODE_1)

        clock.observe(remote)
        val stamp = clock.tick()

        assertThat(remote.relate(stamp.clock)).isEqualTo(Relation.BEFORE)
    }

    @Test
    fun `the order puts a cause before its effect`() {
        val first = ClockStamp(VectorClock().tick(NODE_1), NODE_1)
        val second = ClockStamp(first.clock.tick(NODE_1), NODE_1)

        assertThat(ClockStamp.ORDER.compare(first, second)).isNegative()
    }

    /**
     * **The total order that a subtractive grant needs.** Two concurrent
     * stamps are ordered by the node that wrote them, so a reader reaches one
     * answer rather than two.
     */
    @Test
    fun `the order separates two concurrent stamps by their node`() {
        val fromOne = ClockStamp(VectorClock().tick(NODE_1), NODE_1)
        val fromTwo = ClockStamp(VectorClock().tick(NODE_2), NODE_2)

        assertThat(fromOne.clock.relate(fromTwo.clock)).isEqualTo(Relation.CONCURRENT)
        assertThat(ClockStamp.ORDER.compare(fromOne, fromTwo)).isNegative()
        assertThat(ClockStamp.ORDER.compare(fromTwo, fromOne)).isPositive()
    }

    @Test
    fun `the order sorts a mixed list the same way every time`() {
        val a = ClockStamp(VectorClock().tick(NODE_1), NODE_1)
        val b = ClockStamp(a.clock.tick(NODE_1), NODE_1)
        val c = ClockStamp(VectorClock().tick(NODE_2), NODE_2)

        assertThat(listOf(c, b, a).sortedWith(ClockStamp.ORDER)).containsExactly(a, b, c)
        assertThat(listOf(b, a, c).sortedWith(ClockStamp.ORDER)).containsExactly(a, b, c)
    }

    /**
     * **A tick must lose no reading.** The owner asked for an atomic
     * operator. Two hundred threads tick once each, and the clock must read
     * two hundred.
     */
    @Test
    fun `a tick loses no reading under many threads`() {
        val clock = NodeClock(NODE_1)
        val pool = Executors.newFixedThreadPool(THREADS)
        val start = CountDownLatch(1)
        val done = CountDownLatch(TICKS)

        repeat(TICKS) {
            pool.submit {
                start.await()
                clock.tick()
                done.countDown()
            }
        }
        start.countDown()

        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue()
        pool.shutdown()
        assertThat(clock.read().countOf(NODE_1)).isEqualTo(TICKS.toLong())
    }

    @Test
    fun `every tick of one node answers a different reading`() {
        val clock = NodeClock(NODE_1)
        val pool = Executors.newFixedThreadPool(THREADS)
        val readings = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
        val done = CountDownLatch(TICKS)

        repeat(TICKS) {
            pool.submit {
                readings.add(clock.tick().clock.countOf(NODE_1))
                done.countDown()
            }
        }

        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue()
        pool.shutdown()
        assertThat(readings).hasSize(TICKS)
    }

    private companion object {
        val NODE_1 = NodeId(1)
        val NODE_2 = NodeId(2)
        val NODE_3 = NodeId(3)
        const val THREADS = 16
        const val TICKS = 200
    }
}
