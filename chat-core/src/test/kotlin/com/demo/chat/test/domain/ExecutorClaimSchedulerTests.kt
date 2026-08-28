package com.demo.chat.test.domain

import com.demo.chat.domain.ExecutorClaimScheduler
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class ExecutorClaimSchedulerTests {

    @Test
    fun `a periodic task reports that it runs on a scheduler thread`() {
        val scheduler = ExecutorClaimScheduler()
        val onScheduler = AtomicReference<Boolean>()
        try {
            scheduler.schedulePeriodic(Duration.ofMillis(20)) {
                onScheduler.compareAndSet(null, scheduler.isSchedulerThread())
            }
            Awaitility.await().atMost(Duration.ofSeconds(2)).until { onScheduler.get() != null }
            Assertions.assertTrue(onScheduler.get())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `a detached task does not run on a scheduler thread`() {
        val scheduler = ExecutorClaimScheduler()
        val onScheduler = AtomicReference<Boolean>()
        try {
            scheduler.runDetached("nodeid-claim-close") {
                onScheduler.set(scheduler.isSchedulerThread())
            }
            Awaitility.await().atMost(Duration.ofSeconds(2)).until { onScheduler.get() != null }
            Assertions.assertFalse(onScheduler.get())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `shutdown from a scheduler thread returns and does not wait for itself`() {
        val scheduler = ExecutorClaimScheduler()
        val done = AtomicReference<Boolean>()
        scheduler.schedulePeriodic(Duration.ofMillis(20)) {
            scheduler.shutdownNow()
            done.compareAndSet(null, true)
        }
        Awaitility.await().atMost(Duration.ofSeconds(2)).until { done.get() == true }
    }

    @Test
    fun `a closed periodic handle stops the task`() {
        val scheduler = ExecutorClaimScheduler()
        try {
            val runs = java.util.concurrent.atomic.AtomicInteger()
            val handle = scheduler.schedulePeriodic(Duration.ofMillis(20)) { runs.incrementAndGet() }
            Awaitility.await().atMost(Duration.ofSeconds(2)).until { runs.get() > 0 }
            handle.close()
            Thread.sleep(100)
            val seen = runs.get()
            Thread.sleep(200)
            Assertions.assertEquals(seen, runs.get())
        } finally {
            scheduler.shutdownNow()
        }
    }
}
