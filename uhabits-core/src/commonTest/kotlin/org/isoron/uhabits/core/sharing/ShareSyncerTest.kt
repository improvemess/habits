/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.isoron.uhabits.core.sharing

import kotlinx.coroutines.test.runTest
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.io.StandardLogging
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareSyncerTest : BaseUnitTest() {
    private val start = LocalDate(2026, 11, 20)
    private lateinit var store: ShareStore
    private lateinit var api: FakeSharingApi
    private lateinit var tokens: MemoryDeviceTokenStore
    private lateinit var syncer: ShareSyncer
    private lateinit var habit: Habit
    private lateinit var share: Share
    private val devices = object : DeviceIdStorage {
        var id: String? = null
        override fun load() = id
        override fun store(deviceId: String) {
            id = deviceId
        }
    }

    private suspend fun init() {
        val db = buildMemoryDatabase()
        store = ShareStore(db)
        api = FakeSharingApi()
        tokens = MemoryDeviceTokenStore()
        habit = fixtures.createEmptyHabit()
        habitList.add(habit)
        share = store.insert("s1", habit.uuid!!, "https://a", ShareKey.generate(), start, 0)
        syncer = ShareSyncer(
            store,
            habitList,
            api,
            tokens,
            OwnerIdentityStore(db),
            DeviceIdentity(devices),
            StandardLogging()
        )
    }

    private fun enqueueEntry(date: LocalDate, value: Int, updatedAt: Long = 1) {
        habit.originalEntries.add(Entry(date, value))
        store.enqueue(OutboxItem(share.id, OutboxItem.Kind.ENTRY, date, updatedAt))
    }

    @Test
    fun testRegistersOnceAndSendsMetaAndEntries() = runTest {
        init()
        store.enqueue(OutboxItem(share.id, OutboxItem.Kind.META, null, 1))
        enqueueEntry(start, Entry.YES_MANUAL, updatedAt = 7)
        assertEquals(ShareSyncer.Result.DONE, syncer.sync())
        assertEquals(listOf("createOwner", "registerDevice", "putMeta", "putEntries"), api.calls)
        assertEquals("dt_1", tokens.get("https://a"))
        assertTrue(store.sharesWithPending().isEmpty())

        val meta = SharePayloads.openMetadata(share.key, "s1", api.metas["s1"]!!)
        assertEquals(habit.name, meta.name)
        assertEquals(start, meta.historyStart)
        val upload = api.entries["s1"]!!.single()
        assertEquals(start, upload.date)
        assertEquals(7, upload.updatedAt)
        assertEquals(devices.id, upload.deviceId)
        assertEquals(SharedEntry(SharedEntry.State.YES), SharePayloads.openEntry(share.key, "s1", start, upload.data))

        api.calls.clear()
        enqueueEntry(start.plus(1), Entry.NO)
        syncer.sync()
        assertEquals(listOf("putEntries"), api.calls)
    }

    @Test
    fun testSendsTheLatestStateOfAnEntry() = runTest {
        init()
        enqueueEntry(start, Entry.YES_MANUAL, updatedAt = 1)
        enqueueEntry(start, Entry.SKIP, updatedAt = 2)
        syncer.sync()
        val upload = api.entries["s1"]!!.single()
        assertEquals(2, upload.updatedAt)
        assertEquals(SharedEntry.State.SKIP, SharePayloads.openEntry(share.key, "s1", start, upload.data).state)
    }

    @Test
    fun testTemporaryFailureKeepsOutboxAndAsksForRetry() = runTest {
        init()
        enqueueEntry(start, Entry.YES_MANUAL)
        api.failNext("putEntries", null)
        assertEquals(ShareSyncer.Result.RETRY, syncer.sync())
        assertEquals(1, store.pending(share.id).size)
        assertEquals(ShareSyncer.Result.DONE, syncer.sync())
        assertTrue(store.pending(share.id).isEmpty())
    }

    @Test
    fun testServerErrorAsksForRetry() = runTest {
        init()
        enqueueEntry(start, Entry.YES_MANUAL)
        api.failNext("registerDevice", 503)
        assertEquals(ShareSyncer.Result.RETRY, syncer.sync())
        assertNull(tokens.get("https://a"))
        assertEquals(1, store.pending(share.id).size)
    }

    @Test
    fun testRejectedTokenRegistersAgain() = runTest {
        init()
        tokens.put("https://a", "dt_old")
        enqueueEntry(start, Entry.YES_MANUAL)
        api.failNext("putEntries", 401)
        assertEquals(ShareSyncer.Result.DONE, syncer.sync())
        assertEquals(listOf("putEntries!401", "createOwner", "registerDevice", "putEntries"), api.calls)
        assertEquals("dt_1", tokens.get("https://a"))
        assertTrue(store.pending(share.id).isEmpty())
    }

    @Test
    fun testShareGoneOnServerIsRemovedLocally() = runTest {
        init()
        enqueueEntry(start, Entry.YES_MANUAL)
        api.failNext("putEntries", 410)
        assertEquals(ShareSyncer.Result.DONE, syncer.sync())
        assertNull(store.findById(share.id))
    }

    @Test
    fun testRejectedItemsAreDroppedNotRetriedForever() = runTest {
        init()
        enqueueEntry(start, Entry.YES_MANUAL)
        api.failNext("putEntries", 400)
        assertEquals(ShareSyncer.Result.DONE, syncer.sync())
        assertTrue(store.pending(share.id).isEmpty())
        assertTrue(store.findById(share.id) != null)
    }

    @Test
    fun testEndedShareIsEndedOnServerAndRemoved() = runTest {
        init()
        store.markEnded(share.id)
        store.enqueue(OutboxItem(share.id, OutboxItem.Kind.END, null, 1))
        syncer.sync()
        assertEquals(listOf("createOwner", "registerDevice", "endShare"), api.calls)
        assertNull(store.findById(share.id))
    }

    @Test
    fun testShareOfMissingHabitIsEnded() = runTest {
        init()
        store.enqueue(OutboxItem(share.id, OutboxItem.Kind.META, null, 1))
        habitList.remove(habit)
        syncer.sync()
        assertTrue(api.calls.contains("endShare"))
        assertNull(store.findById(share.id))
    }

    @Test
    fun testEntriesBeforeHistoryStartAreDroppedUnsent() = runTest {
        init()
        enqueueEntry(start.minus(1), Entry.YES_MANUAL)
        syncer.sync()
        assertTrue(api.entries.isEmpty())
        assertTrue(store.pending(share.id).isEmpty())
    }

    @Test
    fun testHeartbeatGoesToEveryServerWithActiveShares() = runTest {
        init()
        store.insert("s2", habit.uuid!!, "https://b", ShareKey.generate(), start, 0)
        val ended = store.insert("s3", habit.uuid!!, "https://c", ShareKey.generate(), start, 0)
        store.markEnded(ended.id)
        assertEquals(ShareSyncer.Result.DONE, syncer.heartbeat())
        assertEquals(listOf("heartbeat:https://a", "heartbeat:https://b"), api.calls.filter { it.startsWith("heartbeat") })
    }
}
