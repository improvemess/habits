package org.isoron.uhabits.core.database.migrations

import kotlinx.coroutines.test.runTest
import org.isoron.platform.io.Database
import org.isoron.platform.io.format
import org.isoron.platform.io.migrateTo
import org.isoron.platform.io.query
import org.isoron.platform.io.run
import org.isoron.uhabits.core.BaseUnitTest
import kotlin.test.Test
import kotlin.test.assertEquals

class Version28Test : BaseUnitTest() {

    private lateinit var db: Database

    private suspend fun initDb() {
        db = openDatabaseResource("/databases/022.db")
    }

    private suspend fun migrateTo(version: Int) {
        db.migrateTo(version) { v ->
            val path = "migrations/${format("%02d.sql", v)}"
            fileOpener.openResourceFile(path).lines().joinToString("\n")
        }
    }

    private fun dbTest(block: suspend () -> Unit) = runTest {
        initDb()
        block()
    }

    @Test
    fun testMigrateTo28AddsTitleColumnWithEmptyDefault() = dbTest {
        migrateTo(28)
        db.run("insert into HabitPauses(habit, startTimestamp, endTimestamp) values (1, 10, 20)")
        db.query("select title from HabitPauses") { stmt ->
            assertEquals("", stmt.getText(0))
        }
    }
}
