package com.rodrigos01.aipodcasts.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionLedgerTest {

    private data class Published(val id: String, val positionMs: Long, val updatedAt: Long, val urgent: Boolean)

    private class MapStorage : LongStorage {
        val map = mutableMapOf<String, Long>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: Long) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private var now = 1_000L
    private val published = mutableListOf<Published>()
    private val ledger = PositionLedger(
        storage = MapStorage(),
        publisher = { id, pos, ts, urgent -> published += Published(id, pos, ts, urgent) },
        clock = { now }
    )

    @Test
    fun savedPositionIsReadBack_andClearedToZero() {
        ledger.savePositionMs("e1", 45_000)
        assertEquals(45_000, ledger.getPositionMs("e1"))
        ledger.clearPosition("e1")
        assertEquals(0, ledger.getPositionMs("e1"))
    }

    @Test
    fun progressTicksAreThrottled_butCheckpointsPublishImmediately() {
        ledger.savePositionMs("e1", 1_000)              // first write publishes
        now += 500
        ledger.savePositionMs("e1", 1_500)              // tick: throttled
        now += 500
        ledger.savePositionMs("e1", 2_000)              // tick: throttled
        assertEquals(1, published.size)

        now += 500
        ledger.savePositionMs("e1", 2_500, checkpoint = true)
        assertEquals(2, published.size)
        assertTrue(published.last().urgent)
        assertEquals(2_500, published.last().positionMs)

        now += PositionLedger.PUBLISH_INTERVAL_MS
        ledger.savePositionMs("e1", 20_000)             // interval elapsed
        assertEquals(3, published.size)
    }

    @Test
    fun newerRemotePositionWins_olderOrEqualIsIgnored() {
        now = 5_000
        ledger.savePositionMs("e1", 10_000)

        assertFalse(ledger.applyRemote("e1", 99_000, updatedAt = 4_000))
        assertFalse(ledger.applyRemote("e1", 99_000, updatedAt = 5_000))
        assertEquals(10_000, ledger.getPositionMs("e1"))

        assertTrue(ledger.applyRemote("e1", 99_000, updatedAt = 6_000))
        assertEquals(99_000, ledger.getPositionMs("e1"))
    }

    @Test
    fun remoteFinishClearsLocalPosition_andOlderPositionCannotBringItBack() {
        now = 5_000
        ledger.savePositionMs("e1", 10_000)

        assertTrue(ledger.applyRemote("e1", 0, updatedAt = 7_000)) // finished on the other device
        assertEquals(0, ledger.getPositionMs("e1"))
        assertFalse(ledger.applyRemote("e1", 10_000, updatedAt = 6_000))
        assertEquals(0, ledger.getPositionMs("e1"))
    }

    @Test
    fun clearingPublishesATombstoneSoItPropagates() {
        ledger.savePositionMs("e1", 10_000)
        now += 100
        ledger.clearPosition("e1")
        assertEquals(Published("e1", 0, now, true), published.last())
    }

    @Test
    fun positionsWithNoTimestampYield_toAnyRemoteValue() {
        // Positions saved before syncing existed have no timestamp; the other device's wins.
        val storage = MapStorage().apply { put("pos_e1", 30_000) }
        val legacy = PositionLedger(storage, { _, _, _, _ -> }, { now })
        assertTrue(legacy.applyRemote("e1", 50_000, updatedAt = 1))
        assertEquals(50_000, legacy.getPositionMs("e1"))
    }
}
