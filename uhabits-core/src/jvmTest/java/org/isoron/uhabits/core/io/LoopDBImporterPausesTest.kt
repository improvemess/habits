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
import org.isoron.platform.io.Database
import org.isoron.platform.io.TestDatabaseHelper
import org.isoron.platform.io.UserFile
import org.isoron.platform.io.migrateTo
import org.isoron.platform.io.querySingle
import org.isoron.platform.io.run
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.DATABASE_VERSION
import org.isoron.uhabits.core.models.Pause
import org.isoron.uhabits.core.sharing.OwnerIdentityStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Imports of backups that carry pauses. JVM only, see [LoopDBImporterOwnerIdentityTest].
 */
class LoopDBImporterPausesTest : BaseUnitTest() {

    private val start = LocalDate(9000)
    private val end = LocalDate(9004)
    private lateinit var uuid: String

    @Test
    fun testBackupPausesAreImportedWithTitle() = runTest {
        importBackup()
        val habit = habitList.getByUUID(uuid)!!
        assertEquals(listOf(Pause(start, end, "Vacation")), habit.pauses.getAll())
    }

    @Test
    fun testImportingTwiceDoesNotDuplicatePauses() = runTest {
        importBackup()
        importBackup()
        val habit = habitList.getByUUID(uuid)!!
        assertEquals(1, habit.pauses.getAll().size)
    }

    @Test
    fun testBackupWithoutPausesImportsFine() = runTest {
        importBackup(withPause = false)
        assertEquals(emptyList(), habitList.getByUUID(uuid)!!.pauses.getAll())
    }

    private suspend fun importBackup(withPause: Boolean = true) {
        val userFile: UserFile = copyResourceToTempFile("loop.db")
        val db = databaseOpener().open(userFile.pathString)
        db.migrateTo(DATABASE_VERSION) { TestDatabaseHelper.loadMigrationSQL(it) }
        val habitId = db.querySingle("SELECT id FROM Habits ORDER BY id LIMIT 1") { it.getLong(0) }!!
        uuid = db.querySingle("SELECT uuid FROM Habits WHERE id = $habitId") { it.getText(0) }!!
        if (withPause) insertPause(db, habitId)
        db.close()

        val importer = LoopDBImporter(
            habitList,
            modelFactory,
            databaseOpener(),
            commandRunner,
            StandardLogging(),
            fileOpener,
            OwnerIdentityStore(buildMemoryDatabase())
        )
        importer.importHabitsFromFile(userFile)
        userFile.delete()
    }

    private fun insertPause(db: Database, habitId: Long) {
        db.run(
            "INSERT INTO HabitPauses(habit, startTimestamp, endTimestamp, title) " +
                "VALUES ($habitId, ${start.unixTime}, ${end.unixTime}, 'Vacation')"
        )
    }
}
