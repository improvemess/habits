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
import org.isoron.platform.io.queryInt
import org.isoron.platform.io.run
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OwnerIdentityStoreTest {
    private val databases = mutableListOf<Database>()

    private suspend fun database(): Database = TestDatabaseHelper.createEmptyDatabase().also { databases.add(it) }

    // sql.js keeps every open database in one WebAssembly heap; close them, or later tests run out of memory.
    @AfterTest
    fun closeDatabases() {
        databases.forEach { it.close() }
    }

    @Test
    fun testLoadReturnsNullWhenEmpty() = runTest {
        val store = OwnerIdentityStore(database())
        assertNull(store.load())
    }

    @Test
    fun testGetOrCreateIsStable() = runTest {
        val db = database()
        val first = OwnerIdentityStore(db).getOrCreate()
        val second = OwnerIdentityStore(db).getOrCreate()
        assertEquals(first, second)
        assertEquals(first, OwnerIdentityStore(db).load())
        assertEquals(1, db.queryInt("SELECT count(*) FROM OwnerIdentity"))
    }

    @Test
    fun testSaveReplacesExistingIdentity() = runTest {
        val db = database()
        val store = OwnerIdentityStore(db)
        store.getOrCreate()
        val other = OwnerIdentity.generate()
        store.save(other)
        assertEquals(other, store.load())
        assertEquals(1, db.queryInt("SELECT count(*) FROM OwnerIdentity"))
    }

    @Test
    fun testMalformedRowIsIgnored() = runTest {
        val db = database()
        db.run("INSERT INTO OwnerIdentity(id, owner_id, owner_secret) VALUES (1, 'x', 'y')")
        val store = OwnerIdentityStore(db)
        assertNull(store.load())
        val created = store.getOrCreate()
        assertEquals(created, store.load())
    }
}
