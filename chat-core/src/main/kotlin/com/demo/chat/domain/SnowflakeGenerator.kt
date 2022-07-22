package com.demo.chat.domain

import com.demo.chat.service.IKeyGenerator
import java.net.NetworkInterface
import java.security.SecureRandom
import java.time.Instant

/**
 * Converted to Kotlin mostly by machine..
 * From: https://www.callicoder.com/distributed-unique-id-sequence-number-generator/
 * Distributed Sequence Generator.
 * Inspired by Twitter snowflake: https://github.com/twitter/snowflake/tree/snowflake-2010
 *
 * This class should be used as a Singleton.
 * Make sure that you create and reuse a Single instance of SequenceGenerator per node in your distributed system cluster.
 */
class SnowflakeGenerator : IKeyGenerator<Long> {

    constructor(nodeId: Int) {
        require(!(nodeId < 0 || nodeId > maxNodeId)) { String.format("NodeId must be between %d and %d", 0, maxNodeId) }
        this.nodeId = nodeId
    }

    // Let SequenceGenerator generate a nodeId
    constructor() {
        nodeId = createNodeId()

    }

    private val UNUSED_BITS = 1 // Sign bit, Unused (always set to 0)

    private val EPOCH_BITS = 41
    private val NODE_ID_BITS = 10
    private val SEQUENCE_BITS = 12

    private val maxNodeId = (Math.pow(2.0, NODE_ID_BITS.toDouble()) - 1).toInt()
    private val maxSequence = (Math.pow(2.0, SEQUENCE_BITS.toDouble()) - 1).toInt()

    // Custom Epoch (January 1, 2015 Midnight UTC = 2015-01-01T00:00:00Z)
    private val CUSTOM_EPOCH = 1420070400000L

    private var nodeId = 0

    @Volatile
    private var lastTimestamp = -1L

    @Volatile
    private var sequence = 0L

    @Synchronized
    override fun nextKey(): Long{
        var currentTimestamp = timestamp()
        check(currentTimestamp >= lastTimestamp) { "Invalid System Clock!" }
        if (currentTimestamp == lastTimestamp) {
            sequence = sequence + 1 and maxSequence.toLong()
            if (sequence == 0L) {
                // Sequence Exhausted, wait till next millisecond.
                currentTimestamp = waitNextMillis(currentTimestamp)
            }
        } else {
            // reset sequence to start with zero for the next millisecond
            sequence = 0
        }
        lastTimestamp = currentTimestamp
        var id = currentTimestamp shl NODE_ID_BITS + SEQUENCE_BITS
        id = id or (nodeId shl SEQUENCE_BITS).toLong()
        id = id or sequence
        return id
    }


    // Get current timestamp in milliseconds, adjust for the custom epoch.
    private fun timestamp(): Long {
        return Instant.now().toEpochMilli() - CUSTOM_EPOCH
    }

    // Block and wait till next millisecond
    private fun waitNextMillis(currentTimestamp: Long): Long {
        var currentTimestamp = currentTimestamp
        while (currentTimestamp == lastTimestamp) {
            currentTimestamp = timestamp()
        }
        return currentTimestamp
    }

    private fun createNodeId(): Int {
        var nodeId: Int
        nodeId = try {
            val sb = StringBuilder()
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val mac = networkInterface.hardwareAddress
                if (mac != null) {
                    for (i in mac.indices) {
                        sb.append(String.format("%02X", mac[i]))
                    }
                }
            }
            sb.toString().hashCode()
        } catch (ex: Exception) {
            SecureRandom().nextInt()
        }
        nodeId = nodeId and maxNodeId
        return nodeId
    }
}