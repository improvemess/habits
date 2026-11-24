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
import org.isoron.platform.io.run
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.DATABASE_VERSION
import org.isoron.uhabits.core.sharing.OwnerIdentity
import org.isoron.uhabits.core.sharing.OwnerIdentityStore
import org.isoron.uhabits.core.sharing.ShareKey
import org.isoron.uhabits.core.sharing.ShareStore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Imports of backups that carry an owner identity. JVM only: preparing such a
 * backup means writing to a database file, which the JS test opener keeps in
 * memory only. Backups without an identity are covered by [ImportTest].
 */
class LoopDBImporterOwnerIdentityTest : BaseUnitTest() {

    private lateinit var ownerIdentityStore: OwnerIdentityStore
    private lateinit var shareStore: ShareStore

    private suspend fun initStores() {
        val db = buildMemoryDatabase()
        ownerIdentityStore = OwnerIdentityStore(db)
        shareStore = ShareStore(db)
    }

    @Test
    fun testBackupWithOwnerIdentityReplacesLocalIdentity() = runTest {
        initStores()
        ownerIdentityStore.getOrCreate()
        val backedUp = OwnerIdentity.generate()
        importBackup { db -> OwnerIdentityStore(db).save(backedUp) }
        assertEquals(backedUp, ownerIdentityStore.load())
    }

    @Test
    fun testBackupWithMalformedOwnerIdentityKeepsLocalIdentity() = runTest {
        initStores()
        val local = ownerIdentityStore.getOrCreate()
        importBackup { db ->
            db.run(
                "INSERT INTO OwnerIdentity(id, owner_id, owner_secret) " +
                    "VALUES (1, 'not-a-uuid', 'not-a-secret')"
            )
        }
        assertEquals(local, ownerIdentityStore.load())
    }

    @Test
    fun testBackupWithOtherIdentityIsIgnoredWhileSharesAreActive() = runTest {
        initStores()
        val local = ownerIdentityStore.getOrCreate()
        shareStore.insert("s1", "h1", "https://a", ShareKey.generate(), LocalDate(2026, 11, 24), 0)
        importBackup { db -> OwnerIdentityStore(db).save(OwnerIdentity.generate()) }
        assertEquals(local, ownerIdentityStore.load())
    }

    @Test
    fun testBackupWithSameIdentityIsFineWhileSharesAreActive() = runTest {
        initStores()
        val local = ownerIdentityStore.getOrCreate()
        shareStore.insert("s1", "h1", "https://a", ShareKey.generate(), LocalDate(2026, 11, 24), 0)
        importBackup { db -> OwnerIdentityStore(db).save(local) }
        assertEquals(local, ownerIdentityStore.load())
    }

    /** Migrates a copy of loop.db to the current version, lets [prepare] change it, imports it. */
    private suspend fun importBackup(prepare: (Database) -> Unit) {
        val userFile: UserFile = copyResourceToTempFile("loop.db")
        val db = databaseOpener().open(userFile.pathString)
        db.migrateTo(DATABASE_VERSION) { TestDatabaseHelper.loadMigrationSQL(it) }
        prepare(db)
        db.close()

        val importer = LoopDBImporter(
            habitList,
            modelFactory,
            databaseOpener(),
            commandRunner,
            StandardLogging(),
            fileOpener,
            ownerIdentityStore,
            shareStore
        )
        importer.importHabitsFromFile(userFile)
        userFile.delete()
    }
}
