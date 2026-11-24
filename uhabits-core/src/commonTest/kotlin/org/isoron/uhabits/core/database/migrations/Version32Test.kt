package org.isoron.uhabits.core.database.migrations

import kotlinx.coroutines.test.runTest
import org.isoron.platform.io.Database
import org.isoron.platform.io.format
import org.isoron.platform.io.migrateTo
import org.isoron.platform.io.query
import org.isoron.platform.io.run
import org.isoron.uhabits.core.BaseUnitTest
import kotlin.test.Test
import kotlin.test.assertTrue

class Version32Test : BaseUnitTest() {
    private lateinit var db: Database

    @Test
    fun testMigrateTo32AddsNullableNegativeSinceColumn() = runTest {
        db = openDatabaseResource("/databases/022.db")
        db.migrateTo(32) { v ->
            val path = "migrations/${format("%02d.sql", v)}"
            fileOpener.openResourceFile(path).lines().joinToString("\n")
        }
        var rows = 0
        db.query("select negative_since from Habits") { stmt ->
            rows++
            assertTrue(stmt.getLongOrNull(0) == null)
        }
        assertTrue(rows > 0)
        db.run("update Habits set negative_since = 5")
    }
}
