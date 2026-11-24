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
import org.isoron.platform.io.TestDatabaseHelper
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.commands.EntrySource
import org.isoron.uhabits.core.events.HabitEvent
import org.isoron.uhabits.core.events.HabitLifecycleEvent
import org.isoron.uhabits.core.events.HabitSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FriendsTransportTest {
    private val start = LocalDate(2026, 11, 20)
    private var changes = 0

    private suspend fun setup(): Pair<ShareStore, FriendsTransport> {
        val store = ShareStore(TestDatabaseHelper.createEmptyDatabase())
        store.insert("s1", "h1", "https://a", ShareKey.generate(), start, 0)
        return store to FriendsTransport(store, { 42 }, { changes++ })
    }

    private fun entryEvent(uuid: String, date: LocalDate) = HabitEvent(
        habitUuid = uuid, habitName = "Run", category = "", date = date,
        state = HabitEvent.EntryState.YES, value = 1.0, habitType = HabitEvent.Type.YES_NO,
        unit = "", target = null, targetType = null, completed = true, currentStreak = 1,
        score = 0.1, source = EntrySource.APP
    )

    private val snapshot = HabitSnapshot(
        "h1", "Run", "", "", "", 3, 1, 1, HabitEvent.Type.YES_NO, "", null, null
    )

    @Test
    fun testEntryChangeIsEnqueued() = runTest {
        val (store, transport) = setup()
        val share = store.findByShareId("s1")!!
        transport.send(entryEvent("h1", start.plus(2)))
        assertEquals(listOf(OutboxItem(share.id, OutboxItem.Kind.ENTRY, start.plus(2), 42)), store.pending(share.id))
        assertEquals(1, changes)
    }

    @Test
    fun testDaysBeforeHistoryStartAreNotShared() = runTest {
        val (store, transport) = setup()
        transport.send(entryEvent("h1", start.minus(1)))
        assertTrue(store.sharesWithPending().isEmpty())
        assertEquals(0, changes)
    }

    @Test
    fun testUnsharedHabitsAreIgnored() = runTest {
        val (store, transport) = setup()
        transport.send(entryEvent("other", start))
        transport.send(HabitLifecycleEvent.Updated(snapshot.copy(habitUuid = "other")))
        assertTrue(store.sharesWithPending().isEmpty())
        assertEquals(0, changes)
    }

    @Test
    fun testUpdateEnqueuesMeta() = runTest {
        val (store, transport) = setup()
        val share = store.findByShareId("s1")!!
        transport.send(HabitLifecycleEvent.Updated(snapshot))
        assertEquals(listOf(OutboxItem(share.id, OutboxItem.Kind.META, null, 42)), store.pending(share.id))
    }

    @Test
    fun testDeleteEndsShare() = runTest {
        val (store, transport) = setup()
        val share = store.findByShareId("s1")!!
        transport.send(HabitLifecycleEvent.Deleted("h1"))
        assertTrue(store.findById(share.id)!!.ended)
        assertEquals(listOf(OutboxItem(share.id, OutboxItem.Kind.END, null, 42)), store.pending(share.id))
        // Ended shares receive nothing more.
        transport.send(entryEvent("h1", start))
        assertEquals(1, store.pending(share.id).size)
    }

    @Test
    fun testArchiveDoesNotEndShare() = runTest {
        val (store, transport) = setup()
        transport.send(HabitLifecycleEvent.Archived("h1"))
        assertTrue(store.findActiveByHabit("h1").isNotEmpty())
        assertTrue(store.sharesWithPending().isEmpty())
    }
}
