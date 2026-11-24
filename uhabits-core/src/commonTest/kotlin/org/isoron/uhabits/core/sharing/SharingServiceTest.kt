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
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.setToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharingServiceTest : BaseUnitTest() {
    private val today = LocalDate(2026, 11, 24)
    private val server = "https://habits.example.com"
    private lateinit var db: Database
    private lateinit var store: ShareStore
    private lateinit var api: FakeSharingApi
    private lateinit var service: SharingService
    private lateinit var habit: Habit
    private var outboxChanges = 0

    @AfterTest
    fun closeDatabase() {
        if (::db.isInitialized) db.close()
    }

    private suspend fun init() {
        setToday(today)
        db = buildMemoryDatabase()
        store = ShareStore(db)
        api = FakeSharingApi()
        val devices = object : DeviceIdStorage {
            var id: String? = null
            override fun load() = id
            override fun store(deviceId: String) {
                id = deviceId
            }
        }
        val sessions = ServerSessions(api, MemoryDeviceTokenStore(), OwnerIdentityStore(db), DeviceIdentity(devices))
        service = SharingService(store, api, sessions, { 99 }, { outboxChanges++ })
        habit = fixtures.createEmptyHabit()
        habitList.add(habit)
        habit.originalEntries.add(Entry(today.minus(10), Entry.YES_MANUAL))
        habit.originalEntries.add(Entry(today.minus(1), Entry.NO))
        habit.originalEntries.add(Entry(today, Entry.YES_MANUAL))
    }

    private fun pendingEntryDates(share: Share) =
        store.pending(share.id).filter { it.kind == OutboxItem.Kind.ENTRY }.map { it.date }

    @Test
    fun testShareFromToday() = runTest {
        init()
        val share = service.share(habit, server, SharedHistory.FROM_TODAY)
        assertEquals(today, share.historyStart)
        assertEquals(listOf<LocalDate?>(today), pendingEntryDates(share))
        assertEquals(listOf(share), service.sharesOf(habit))
        assertEquals(1, outboxChanges)
    }

    @Test
    fun testShareAllHistoryStartsAtFirstEntry() = runTest {
        init()
        val share = service.share(habit, server, SharedHistory.ALL)
        assertEquals(today.minus(10), share.historyStart)
        assertEquals(setOf(today.minus(10), today.minus(1), today), pendingEntryDates(share).toSet())
    }

    @Test
    fun testShareAllWithoutEntriesStartsToday() = runTest {
        init()
        val empty = fixtures.createEmptyHabit()
        habitList.add(empty)
        assertEquals(today, service.share(empty, server, SharedHistory.ALL).historyStart)
    }

    @Test
    fun testMetadataIsBoundToTheAssignedShareId() = runTest {
        init()
        val share = service.share(habit, server, SharedHistory.FROM_TODAY)
        assertEquals(listOf("createOwner", "registerDevice", "createShare", "putMeta"), api.calls)
        val meta = SharePayloads.openMetadata(share.key, share.shareId, api.metas[share.shareId]!!)
        assertEquals(habit.name, meta.name)
        assertEquals(today, meta.historyStart)
        assertTrue(store.pending(share.id).none { it.kind == OutboxItem.Kind.META })
    }

    @Test
    fun testFailedMetaUploadIsRetriedThroughOutbox() = runTest {
        init()
        api.failNext("putMeta", null)
        val share = service.share(habit, server, SharedHistory.FROM_TODAY)
        assertTrue(store.pending(share.id).any { it.kind == OutboxItem.Kind.META })
    }

    @Test
    fun testInviteLinkCarriesKeyInFragment() = runTest {
        init()
        val share = service.share(habit, server, SharedHistory.FROM_TODAY)
        val link = service.createInvite(share)
        assertEquals("$server/invite/code1#k=${share.key.encode()}&v=1", link.url)
        assertEquals("2026-12-01T10:00:00.000Z", link.expiresAt)
    }

    @Test
    fun testReadersAndRemoveReader() = runTest {
        init()
        val share = service.share(habit, server, SharedHistory.FROM_TODAY)
        api.readers[share.shareId] = mutableListOf(
            Reader("r1", "Anna", "2026-11-24T07:00:00.000Z"),
            Reader("r2", null, "2026-11-23T07:00:00.000Z")
        )
        assertEquals(listOf("r1", "r2"), service.readers(share).map { it.readerId })
        service.removeReader(share, "r1")
        assertEquals(listOf("r2"), service.readers(share).map { it.readerId })
    }

    @Test
    fun testStopSharingWorksOffline() = runTest {
        init()
        val share = service.share(habit, server, SharedHistory.FROM_TODAY)
        api.calls.clear()
        service.stopSharing(share)
        assertTrue(api.calls.isEmpty())
        assertTrue(service.sharesOf(habit).isEmpty())
        assertTrue(store.pending(share.id).any { it.kind == OutboxItem.Kind.END })
    }

    @Test
    fun testCheckServer() = runTest {
        init()
        assertEquals(ServerCheck.Ok(server, ServerInfo(listOf("v1"), "1.0.1")), service.checkServer("  $server/ "))
        assertIs<ServerCheck.InvalidUrl>(service.checkServer("habits.example.com"))
        assertIs<ServerCheck.InvalidUrl>(service.checkServer("http://habits.example.com"))
        api.failNext("discover", null)
        assertIs<ServerCheck.Unreachable>(service.checkServer(server))
        api.failNext("discover", 404)
        assertIs<ServerCheck.Incompatible>(service.checkServer(server))
        api.apiVersions = listOf("v2")
        assertIs<ServerCheck.Incompatible>(service.checkServer(server))
    }

    @Test
    fun testNormalizeServerUrls() {
        assertEquals(SharingServers.DEFAULT, SharingServers.normalize(SharingServers.DEFAULT))
        assertEquals("https://example.com/habits", SharingServers.normalize("https://example.com/habits/"))
        assertEquals("http://10.0.2.2:3000", SharingServers.normalize("http://10.0.2.2:3000", allowInsecure = true))
        assertNull(SharingServers.normalize("http://10.0.2.2:3000"))
        assertNull(SharingServers.normalize("ftp://example.com"))
        assertNull(SharingServers.normalize("https://"))
        assertNull(SharingServers.normalize("https://example.com/?x=1"))
        assertNull(SharingServers.normalize("https://exa mple.com"))
    }
}
