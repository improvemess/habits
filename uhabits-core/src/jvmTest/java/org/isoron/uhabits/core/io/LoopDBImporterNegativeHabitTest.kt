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
package org.isoron.uhabits.core.io

import kotlinx.coroutines.test.runTest
import org.isoron.platform.io.TestDatabaseHelper
import org.isoron.platform.io.UserFile
import org.isoron.platform.io.migrateTo
import org.isoron.platform.io.run
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.DATABASE_VERSION
import org.isoron.uhabits.core.models.DueDays
import org.isoron.uhabits.core.models.DueDaysChange
import org.isoron.uhabits.core.models.StreakUnit
import org.isoron.uhabits.core.sharing.OwnerIdentityStore
import org.isoron.uhabits.core.sharing.ShareStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Backups keep the "negative habit" type; backups without it import as normal habits. */
class LoopDBImporterNegativeHabitTest : BaseUnitTest() {

    @Test
    fun testBackupKeepsNegativeSince() = runTest {
        val since = getToday().minus(10)
        importBackup("update Habits set negative_since = ${since.unixTime} where id = (select min(id) from Habits)")
        val negatives = habitList.filter { it.isNegative }
        assertEquals(1, negatives.size)
        assertEquals(since, negatives[0].negativeSince)
        assertTrue(negatives[0].isCompletedToday())
    }

    @Test
    fun testOlderBackupImportsAsNormalHabits() = runTest {
        importBackup(null)
        assertTrue(habitList.size() > 0)
        habitList.forEach { assertNull(it.negativeSince) }
        // Older backups are migrated: yes/no habits stay rolling
        habitList.filter { !it.isNumerical }.forEach { assertFalse(it.calendarPeriod) }
        assertNotNull(habitList.getByPosition(0))
    }

    @Test
    fun testBackupKeepsStreakUnit() = runTest {
        importBackup("update Habits set streak_unit = 2 where id = (select min(id) from Habits)")
        val units = habitList.map { it.streakUnit }
        assertEquals(1, units.count { it == StreakUnit.MONTHS })
        // Habits without a chosen unit stay on automatic
        assertTrue(units.count { it == null } == units.size - 1)
    }

    @Test
    fun testOlderBackupHasAutomaticStreakUnit() = runTest {
        importBackup(null)
        habitList.forEach { assertNull(it.streakUnit) }
    }

    @Test
    fun testBackupKeepsFixedWeekdays() = runTest {
        val history = DueDays(listOf(DueDaysChange(LocalDate(5000), 0b0101010)))
        importBackup("update Habits set due_days = '${history.encode()}' where id = (select min(id) from Habits)")
        assertEquals(1, habitList.count { it.dueDays == history })
        assertEquals(habitList.size() - 1, habitList.count { it.dueDays == DueDays() })
    }

    @Test
    fun testOlderBackupHasNoFixedWeekdays() = runTest {
        importBackup(null)
        habitList.forEach { assertEquals(DueDays(), it.dueDays) }
    }

    @Test
    fun testBackupKeepsCreationDay() = runTest {
        val created = getToday().minus(20)
        importBackup("update Habits set created_on = ${created.unixTime} where id = (select min(id) from Habits)")
        assertEquals(1, habitList.count { it.createdOn == created })
    }

    @Test
    fun testOlderBackupGetsCreationDayFromOldestEntry() = runTest {
        importBackup(null)
        habitList.forEach { habit ->
            assertNotNull(habit.createdOn)
            val oldest = habit.originalEntries.getKnown().minOfOrNull { it.date }
            if (oldest != null) assertEquals(oldest, habit.createdOn)
        }
    }

    @Test
    fun testBackupKeepsYearlyFrequency() = runTest {
        importBackup(
            "update Habits set freq_num = 4, freq_den = 365, calendar_period = 1 " +
                "where id = (select min(id) from Habits)"
        )
        val yearly = habitList.filter { it.frequency.isYearly }
        assertEquals(1, yearly.size)
        assertEquals(4, yearly[0].frequency.numerator)
        assertTrue(yearly[0].calendarPeriod)
    }

    @Test
    fun testBackupKeepsCountDays() = runTest {
        importBackup("update Habits set count_days = 1 where id = (select min(id) from Habits)")
        assertEquals(1, habitList.count { it.countDays })
    }

    @Test
    fun testOlderBackupCountsNoDays() = runTest {
        importBackup(null)
        habitList.forEach { assertFalse(it.countDays) }
    }

    private suspend fun importBackup(sql: String?) {
        val userFile: UserFile = copyResourceToTempFile("loop.db")
        val db = databaseOpener().open(userFile.pathString)
        db.migrateTo(DATABASE_VERSION) { TestDatabaseHelper.loadMigrationSQL(it) }
        if (sql != null) db.run(sql)
        db.close()
        val importer = LoopDBImporter(
            habitList,
            modelFactory,
            databaseOpener(),
            commandRunner,
            StandardLogging(),
            fileOpener,
            OwnerIdentityStore(buildMemoryDatabase()),
            ShareStore(buildMemoryDatabase())
        )
        importer.importHabitsFromFile(userFile)
        userFile.delete()
    }
}
