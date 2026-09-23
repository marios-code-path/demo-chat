package com.demo.chat.test.clock

import com.demo.chat.domain.NodeId
import com.demo.chat.domain.clock.ClockStamp
import com.demo.chat.domain.clock.NodeClock
import com.demo.chat.domain.clock.VectorClock
import com.demo.chat.domain.clock.VectorClock.Relation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

        // a and c each hold one tick, so the sum ties and the node decides.
        // b holds two ticks, so it follows both.
        assertThat(listOf(c, b, a).sortedWith(ClockStamp.ORDER)).containsExactly(a, c, b)
        assertThat(listOf(b, a, c).sortedWith(ClockStamp.ORDER)).containsExactly(a, c, b)
    }

    /**
     * **The defect this rule replaced.** Measured on 2026-09-23.
     *
     * The first rule compared causality pairwise and broke a tie by the
     * origin. These three stamps then gave `b < a`, `a < c` and `c < b`,
     * which is a cycle. A sort can answer differently for one input, and
     * TimSort can refuse a comparator that does this.
     */
    @Test
    fun `three stamps that formed a cycle no longer form one`() {
        val a = ClockStamp(VectorClock(mapOf(1 to 1L)), NodeId(2))
        val b = ClockStamp(VectorClock(mapOf(2 to 1L)), NodeId(1))
        val c = ClockStamp(VectorClock(mapOf(1 to 2L)), NodeId(0))

        assertThat(a.clock.relate(b.clock)).isEqualTo(Relation.CONCURRENT)
        assertThat(a.clock.relate(c.clock)).isEqualTo(Relation.BEFORE)
        assertThat(c.clock.relate(b.clock)).isEqualTo(Relation.CONCURRENT)

        // **Every order of the three, because a cycle hides from one order.**
        // The first version of this test called assertNoCycle(a, b, c) alone.
        // That pair answers a before b as false, so the check never ran and
        // the test passed against the rule it was written to catch.
        permutationsOf(a, b, c).forEach { (first, second, third) ->
            assertNoCycle(first, second, third)
        }
    }

    private fun permutationsOf(
        a: ClockStamp, b: ClockStamp, c: ClockStamp
    ): List<Triple<ClockStamp, ClockStamp, ClockStamp>> = listOf(
        Triple(a, b, c), Triple(a, c, b), Triple(b, a, c),
        Triple(b, c, a), Triple(c, a, b), Triple(c, b, a)
    )

    /**
     * **The property the first rule broke.** This test fails against a
     * comparator that breaks a causal tie by the origin.
     */
    @Test
    fun `the order is transitive over many random triples`() {
        val random = java.util.Random(20260923L)
        val stamps = (1..60).map { randomStamp(random) }

        stamps.forEach { first ->
            stamps.forEach { second ->
                stamps.forEach { third -> assertNoCycle(first, second, third) }
            }
        }
    }

    /** A cause must never sort after its effect. */
    @Test
    fun `the order never contradicts causality`() {
        val random = java.util.Random(20260924L)

        (1..200).forEach { _ ->
            val before = randomStamp(random)
            val after = ClockStamp(before.clock.tick(NodeId(random.nextInt(4))), before.origin)

            assertThat(before.clock.relate(after.clock)).isEqualTo(Relation.BEFORE)
            assertThat(ClockStamp.ORDER.compare(before, after))
                .withFailMessage("a cause sorted after its effect")
                .isNegative()
        }
    }

    /** A sort of many stamps must not refuse the comparator. */
    @Test
    fun `a sort of many stamps answers one order`() {
        val random = java.util.Random(20260925L)
        val stamps = (1..500).map { randomStamp(random) }

        val once = stamps.sortedWith(ClockStamp.ORDER)
        val twice = stamps.shuffled(java.util.Random(7L)).sortedWith(ClockStamp.ORDER)

        assertThat(once).isEqualTo(twice)
    }

    /** Fails when the three stamps order in a cycle. */
    private fun assertNoCycle(first: ClockStamp, second: ClockStamp, third: ClockStamp) {
        val firstToSecond = ClockStamp.ORDER.compare(first, second)
        val secondToThird = ClockStamp.ORDER.compare(second, third)
        val firstToThird = ClockStamp.ORDER.compare(first, third)

        if (firstToSecond < 0 && secondToThird < 0) {
            assertThat(firstToThird)
                .withFailMessage(
                    "%s before %s and %s before %s, so the first must come before the third",
                    first, second, second, third
                )
                .isNegative()
        }
    }

    // ---- boundaries ----

    /**
     * **A tick must never wrap.** A count that wrapped would read as lower
     * than the count before it, and a cause would then sort after its effect.
     */
    @Test
    fun `a tick at the highest count is refused`() {
        val full = VectorClock(mapOf(NODE_1.value to Long.MAX_VALUE))

        assertThatThrownBy { full.tick(NODE_1) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("would pass")
    }

    /** A stored clock reaches this constructor, so the sum is checked here. */
    @Test
    fun `counts that would pass the highest sum are refused`() {
        assertThatThrownBy {
            VectorClock(mapOf(NODE_1.value to Long.MAX_VALUE, NODE_2.value to 1L))
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("would pass")
    }

    @Test
    fun `a negative count is refused`() {
        assertThatThrownBy { VectorClock(mapOf(NODE_1.value to -1L)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("never negative")
    }

    /** The index of a clock is `app.nodeid`, which is an integer in 0..1023. */
    @Test
    fun `an index outside the node id range is refused`() {
        assertThatThrownBy { VectorClock(mapOf(NodeId.MAX + 1 to 1L)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("app.nodeid")
    }

    /** The guarantee must still hold one tick below the boundary. */
    @Test
    fun `a cause sorts before its effect at the highest counts`() {
        val cause = VectorClock(mapOf(NODE_1.value to Long.MAX_VALUE - 1L))
        val effect = cause.tick(NODE_1)

        assertThat(cause.relate(effect)).isEqualTo(Relation.BEFORE)
        assertThat(
            ClockStamp.ORDER.compare(ClockStamp(cause, NODE_1), ClockStamp(effect, NODE_1))
        ).isNegative()
    }

    @Test
    fun `an empty clock totals zero`() {
        assertThat(VectorClock().total).isEqualTo(0L)
    }

    private fun randomStamp(random: java.util.Random): ClockStamp {
        val counters = (0..3)
            .filter { random.nextBoolean() }
            .associateWith { (random.nextInt(3) + 1).toLong() }

        return ClockStamp(VectorClock(counters), NodeId(random.nextInt(4)))
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
