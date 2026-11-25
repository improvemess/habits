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
import org.isoron.platform.io.Database
import org.isoron.platform.io.TestDatabaseHelper
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.setToday
import org.isoron.uhabits.core.events.HabitEvent
import org.isoron.uhabits.core.events.PausePeriod
import org.isoron.uhabits.core.io.StandardLogging
import org.isoron.uhabits.core.models.Entry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriendsServiceTest {
    private val today = LocalDate(2026, 11, 24)
    private val server = "https://h.example"
    private val key = ShareKey.generate()
    private val link get() = "$server/invite/c1#k=${key.encode()}&v=1"
    private lateinit var db: Database
    private lateinit var store: FriendStore
    private lateinit var api: FakeSharingApi
    private lateinit var service: FriendsService

    private val metadata = SharedHabitMetadata(
        name = "Run",
        description = "",
        category = "",
        color = 3,
        frequencyNumerator = 1,
        frequencyDenominator = 1,
        habitType = HabitEvent.Type.YES_NO,
        unit = "",
        target = null,
        targetType = null,
        historyStart = today.minus(3)
    )

    @AfterTest
    fun closeDatabase() {
        if (::db.isInitialized) db.close()
    }

    private suspend fun init() {
        setToday(today)
        db = TestDatabaseHelper.createEmptyDatabase()
        store = FriendStore(db)
        api = FakeSharingApi()
        service = FriendsService(store, api, { 1 }, StandardLogging())
    }

    private suspend fun content(vararg entries: Pair<LocalDate, SharedEntry>, revision: Long = 5, status: String = "active") =
        ShareContent(
            status = status,
            meta = SharePayloads.sealMetadata(key, "share-of-c1", metadata),
            // Entries changed in this revision, as the server returns them.
            entries = entries.map { (date, entry) ->
                ReadEntry(date, SharePayloads.sealEntry(key, "share-of-c1", date, entry), revision)
            },
            revision = revision
        )

    private val yes = SharedEntry(SharedEntry.State.YES)

    @Test
    fun testAcceptRedeemsAndFetches() = runTest {
        init()
        api.shareContents["share-of-c1"] = content(today to yes, today.minus(1) to yes)
        val result = service.accept(link, " Anna ")
        val share = assertIs<AcceptResult.Accepted>(result).friendShare
        assertEquals("Run", share.metadata?.name)
        assertEquals(5, share.revision)
        assertEquals(mapOf(today to Entry.YES_MANUAL, today.minus(1) to Entry.YES_MANUAL), store.entries(share.id))
        assertEquals(listOf("discover", "redeemInvite", "readShare"), api.calls)
    }

    @Test
    fun testAcceptRejectsBadLinksAndServers() = runTest {
        init()
        assertIs<AcceptResult.InvalidLink>(service.accept("no link here", null))
        assertIs<AcceptResult.InsecureServer>(service.accept("http://h.example/invite/c1#k=${key.encode()}&v=1", null))
        api.failNext("discover", null)
        assertIs<AcceptResult.Unreachable>(service.accept(link, null))
        api.apiVersions = listOf("v2")
        assertIs<AcceptResult.Incompatible>(service.accept(link, null))
        api.apiVersions = listOf("v1")
        api.failNext("redeemInvite", 410)
        assertIs<AcceptResult.InviteGone>(service.accept(link, null))
        assertTrue(store.findAll().isEmpty())
    }

    @Test
    fun testSyncIsIncremental() = runTest {
        init()
        api.shareContents["share-of-c1"] = content(today to yes)
        val share = (service.accept(link, null) as AcceptResult.Accepted).friendShare
        api.shareContents["share-of-c1"] = content(
            today to SharedEntry(SharedEntry.State.UNKNOWN),
            today.minus(2) to SharedEntry(SharedEntry.State.SKIP),
            revision = 9
        )
        assertEquals(FriendsService.Result.DONE, service.sync())
        assertEquals(listOf(0L, 5L), api.sinceRequests)
        assertEquals(mapOf(today.minus(2) to Entry.SKIP), store.entries(share.id))
        assertEquals(9, store.findById(share.id)!!.revision)
    }

    @Test
    fun testStatusChanges() = runTest {
        init()
        api.shareContents["share-of-c1"] = content(today to yes)
        val share = (service.accept(link, null) as AcceptResult.Accepted).friendShare

        api.shareContents["share-of-c1"] = content(status = "inactive")
        service.sync()
        assertEquals(FriendShare.Status.INACTIVE, store.findById(share.id)!!.status)

        api.shareContents["share-of-c1"] = ShareContent("ended", null, emptyList(), 7)
        service.sync()
        val ended = store.findById(share.id)!!
        assertEquals(FriendShare.Status.ENDED, ended.status)
        assertEquals("Run", ended.metadata?.name, "keeps the last metadata to show what ended")

        api.failNext("readShare", 401)
        service.sync()
        assertEquals(FriendShare.Status.REMOVED, store.findById(share.id)!!.status)
    }

    @Test
    fun testUnreachableServerAsksForRetry() = runTest {
        init()
        api.shareContents["share-of-c1"] = content(today to yes)
        service.accept(link, null)
        api.failNext("readShare", 503)
        assertEquals(FriendsService.Result.RETRY, service.sync())
    }

    @Test
    fun testLeaveForgetsEvenOffline() = runTest {
        init()
        api.shareContents["share-of-c1"] = content(today to yes)
        val share = (service.accept(link, null) as AcceptResult.Accepted).friendShare
        api.failNext("leaveShare", null)
        service.leave(share)
        assertNull(store.findById(share.id))
        assertTrue(store.entries(share.id).isEmpty())
    }

    @Test
    fun testHabitOfComputesStreakFromEntries() = runTest {
        init()
        api.shareContents["share-of-c1"] = content(
            today to yes,
            today.minus(1) to yes,
            today.minus(2) to yes,
            // Before the history start: never shown.
            today.minus(5) to yes
        )
        val share = (service.accept(link, null) as AcceptResult.Accepted).friendShare
        val habit = service.habitOf(share)!!
        assertEquals("Run", habit.name)
        assertEquals(Entry.YES_MANUAL, habit.computedEntries.get(today).value)
        assertEquals(Entry.UNKNOWN, habit.originalEntries.get(today.minus(5)).value)
        assertEquals(3, habit.streaks.getBest(1).single().length)
        assertTrue(habit.scores[today].value > 0)
    }

    @Test
    fun testHabitOfKeepsStreakThroughPauses() = runTest {
        init()
        val paused = metadata.copy(pauses = listOf(PausePeriod(today.minus(2), today.minus(1))))
        api.shareContents["share-of-c1"] = ShareContent(
            status = "active",
            meta = SharePayloads.sealMetadata(key, "share-of-c1", paused),
            entries = listOf(today, today.minus(3)).map { date ->
                ReadEntry(date, SharePayloads.sealEntry(key, "share-of-c1", date, yes), 1)
            },
            revision = 1
        )
        val share = (service.accept(link, null) as AcceptResult.Accepted).friendShare
        val habit = service.habitOf(share)!!
        assertTrue(habit.pauses.isPaused(today.minus(1)))
        assertEquals(Entry.SKIP, habit.computedEntries.get(today.minus(1)).value)
        assertEquals(4, habit.streaks.getBest(1).single().length)
    }
}
