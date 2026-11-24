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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareStoreTest {
    private val day = LocalDate(2026, 11, 24)

    private val databases = mutableListOf<Database>()

    private suspend fun store() = ShareStore(TestDatabaseHelper.createEmptyDatabase().also { databases.add(it) })

    // sql.js keeps every open database in one WebAssembly heap; close them, or later tests run out of memory.
    @AfterTest
    fun closeDatabases() {
        databases.forEach { it.close() }
    }

    private fun ShareStore.add(shareId: String = "s1", habit: String = "h1", server: String = "https://a") =
        insert(shareId, habit, server, ShareKey.generate(), day, 1000)

    @Test
    fun testInsertAndFind() = runTest {
        val store = store()
        val key = ShareKey.generate()
        val share = store.insert("s1", "h1", "https://a", key, day, 1000)
        assertEquals(Share(share.id, "s1", "h1", "https://a", key, day, false), store.findByShareId("s1"))
        assertEquals(listOf(share), store.findActiveByHabit("h1"))
        assertEquals(share, store.findById(share.id))
    }

    @Test
    fun testEndedSharesAreNotActive() = runTest {
        val store = store()
        val a = store.add("s1", server = "https://a")
        store.add("s2", server = "https://b")
        store.markEnded(a.id)
        assertTrue(store.findActiveByHabit("h1").none { it.id == a.id })
        assertTrue(store.findById(a.id)!!.ended)
        assertEquals(listOf("https://b"), store.activeServers())
    }

    @Test
    fun testOutboxKeepsOnlyLatestChangePerItem() = runTest {
        val store = store()
        val share = store.add()
        store.enqueue(OutboxItem(share.id, OutboxItem.Kind.ENTRY, day, 1))
        store.enqueue(OutboxItem(share.id, OutboxItem.Kind.ENTRY, day, 2))
        store.enqueue(OutboxItem(share.id, OutboxItem.Kind.ENTRY, day.plus(1), 3))
        store.enqueue(OutboxItem(share.id, OutboxItem.Kind.META, null, 4))
        assertEquals(
            listOf(
                OutboxItem(share.id, OutboxItem.Kind.ENTRY, day, 2),
                OutboxItem(share.id, OutboxItem.Kind.ENTRY, day.plus(1), 3),
                OutboxItem(share.id, OutboxItem.Kind.META, null, 4)
            ),
            store.pending(share.id)
        )
        assertEquals(listOf(share), store.sharesWithPending())
    }

    @Test
    fun testMarkSentKeepsNewerChanges() = runTest {
        val store = store()
        val share = store.add()
        val sent = OutboxItem(share.id, OutboxItem.Kind.ENTRY, day, 1)
        store.enqueue(sent)
        store.enqueue(sent.copy(updatedAt = 2)) // changed again while sending
        store.markSent(sent)
        assertEquals(listOf(sent.copy(updatedAt = 2)), store.pending(share.id))
        store.markSent(sent.copy(updatedAt = 2))
        assertTrue(store.pending(share.id).isEmpty())
        assertTrue(store.sharesWithPending().isEmpty())
    }

    @Test
    fun testDeleteRemovesOutbox() = runTest {
        val store = store()
        val share = store.add()
        store.enqueue(OutboxItem(share.id, OutboxItem.Kind.META, null, 1))
        store.delete(share.id)
        assertNull(store.findById(share.id))
        assertTrue(store.pending(share.id).isEmpty())
    }
}
