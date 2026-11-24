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
package org.isoron.uhabits.core.database.migrations

import kotlinx.coroutines.test.runTest
import org.isoron.platform.io.Database
import org.isoron.platform.io.format
import org.isoron.platform.io.migrateTo
import org.isoron.platform.io.queryInt
import org.isoron.platform.io.run
import org.isoron.uhabits.core.BaseUnitTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class Version27Test : BaseUnitTest() {

    private lateinit var db: Database

    private suspend fun migrateTo(version: Int) {
        db.migrateTo(version) { v ->
            val path = "migrations/${format("%02d.sql", v)}"
            fileOpener.openResourceFile(path).lines().joinToString("\n")
        }
    }

    private fun dbTest(block: suspend () -> Unit) = runTest {
        db = openDatabaseResource("/databases/022.db")
        block()
    }

    @Test
    fun testMigrateTo27CreatesEmptyOwnerIdentityTable() = dbTest {
        migrateTo(27)
        assertEquals(0, db.queryInt("select count(*) from OwnerIdentity"))
    }

    @Test
    fun testOwnerIdentityTableHoldsAtMostOneRow() = dbTest {
        migrateTo(27)
        db.run("insert into OwnerIdentity(id, owner_id, owner_secret) values (1, 'a', 'b')")
        assertFails {
            db.run("insert into OwnerIdentity(id, owner_id, owner_secret) values (2, 'c', 'd')")
        }
    }
}
