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
import org.isoron.platform.io.format
import org.isoron.platform.io.migrateTo
import org.isoron.platform.io.query
import org.isoron.uhabits.core.BaseUnitTest
import kotlin.test.Test
import kotlin.test.assertTrue

class Version36Test : BaseUnitTest() {

    private suspend fun loadMigration(v: Int) =
        fileOpener.openResourceFile("migrations/${format("%02d.sql", v)}").lines().joinToString("\n")

    @Test
    fun testExistingHabitsHaveNoFixedDays() = runTest {
        val db = openDatabaseResource("/databases/022.db")
        db.migrateTo(35) { loadMigration(it) }
        db.migrateTo(36) { loadMigration(it) }
        var count = 0
        db.query("select due_days from Habits") { stmt ->
            count++
            assertTrue(stmt.getText(0).isEmpty())
        }
        assertTrue(count > 0)
    }
}
