package org.isoron.uhabits.core.database.migrations

import kotlinx.coroutines.test.runTest
import org.isoron.platform.io.format
import org.isoron.platform.io.migrateTo
import org.isoron.platform.io.query
import org.isoron.uhabits.core.BaseUnitTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Version38Test : BaseUnitTest() {

    private suspend fun loadMigration(v: Int) =
        fileOpener.openResourceFile("migrations/${format("%02d.sql", v)}").lines().joinToString("\n")

    @Test
    fun testExistingHabitsKeepTheirSumTarget() = runTest {
        val db = openDatabaseResource("/databases/022.db")
        db.migrateTo(37) { loadMigration(it) }
        db.migrateTo(38) { loadMigration(it) }
        var count = 0
        db.query("select count_days from Habits") { stmt ->
            count++
            assertEquals(0, stmt.getInt(0))
        }
        assertTrue(count > 0)
    }
}
