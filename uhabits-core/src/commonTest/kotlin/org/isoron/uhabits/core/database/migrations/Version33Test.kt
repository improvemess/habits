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
import org.isoron.platform.io.run
import org.isoron.uhabits.core.BaseUnitTest
import kotlin.test.Test
import kotlin.test.assertEquals

class Version33Test : BaseUnitTest() {

    private suspend fun loadMigration(v: Int) =
        fileOpener.openResourceFile("migrations/${format("%02d.sql", v)}").lines().joinToString("\n")

    @Test
    fun testMigrateTo33KeepsWeeklyBehavior() = runTest {
        val db = openDatabaseResource("/databases/022.db")
        db.migrateTo(32) { loadMigration(it) }
        db.run("update Habits set type = 0, freq_num = 3, freq_den = 7 where id = (select min(id) from Habits)")
        db.run("update Habits set type = 1, freq_num = 1, freq_den = 30 where id = (select max(id) - 1 from Habits)")
        db.run("update Habits set type = 1, freq_num = 1, freq_den = 7 where id = (select max(id) from Habits)")
        db.migrateTo(33) { loadMigration(it) }

        val values = mutableListOf<Int>()
        db.query("select calendar_week from Habits order by id") { stmt -> values.add(stmt.getInt(0)) }
        // Yes/no goals keep rolling; measurable weekly goals were always evaluated per calendar week
        assertEquals(0, values.first())
        assertEquals(0, values[values.size - 2])
        assertEquals(1, values.last())
    }
}
