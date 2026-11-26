package org.isoron.uhabits.core.database.migrations

import kotlinx.coroutines.test.runTest
import org.isoron.platform.io.format
import org.isoron.platform.io.migrateTo
import org.isoron.platform.io.query
import org.isoron.uhabits.core.BaseUnitTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Version37Test : BaseUnitTest() {

    private suspend fun loadMigration(v: Int) =
        fileOpener.openResourceFile("migrations/${format("%02d.sql", v)}").lines().joinToString("\n")

    @Test
    fun testExistingHabitsGetTheirOldestEntryAsCreationDay() = runTest {
        val db = openDatabaseResource("/databases/022.db")
        db.migrateTo(36) { loadMigration(it) }
        db.migrateTo(37) { loadMigration(it) }
        var count = 0
        db.query(
            "select created_on, (select min(timestamp) from Repetitions where habit = Habits.id) from Habits"
        ) { stmt ->
            count++
            assertNotNull(stmt.getLongOrNull(0))
            val oldest = stmt.getLongOrNull(1)
            if (oldest != null) assertEquals(oldest, stmt.getLongOrNull(0))
        }
        assertTrue(count > 0)
    }
}
