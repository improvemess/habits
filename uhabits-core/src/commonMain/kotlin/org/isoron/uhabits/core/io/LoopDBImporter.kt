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

import me.tatarka.inject.annotations.Inject
import org.isoron.platform.io.Database
import org.isoron.platform.io.DatabaseOpener
import org.isoron.platform.io.FileOpener
import org.isoron.platform.io.UserFile
import org.isoron.platform.io.getVersion
import org.isoron.platform.io.migrateTo
import org.isoron.platform.io.query
import org.isoron.platform.io.querySingle
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.AppScope
import org.isoron.uhabits.core.DATABASE_VERSION
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.commands.CreateHabitCommand
import org.isoron.uhabits.core.commands.EditHabitCommand
import org.isoron.uhabits.core.database.HabitData
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.ModelFactory
import org.isoron.uhabits.core.models.Pause
import org.isoron.uhabits.core.models.sqlite.SQLiteHabitList
import org.isoron.uhabits.core.sharing.OwnerIdentityStore
import org.isoron.uhabits.core.sharing.ShareStore
import org.isoron.uhabits.core.utils.isSQLite3File

/**
 * Class that imports data from database files exported by Loop Habit Tracker.
 */
@Inject
class LoopDBImporter(
    @AppScope val habitList: HabitList,
    @AppScope val modelFactory: ModelFactory,
    @AppScope val opener: DatabaseOpener,
    @AppScope val runner: CommandRunner,
    @AppScope logging: Logging,
    @AppScope val fileOpener: FileOpener,
    @AppScope val ownerIdentityStore: OwnerIdentityStore,
    @AppScope val shareStore: ShareStore
) : AbstractImporter() {

    private val logger = logging.getLogger("LoopDBImporter")

    override suspend fun canHandle(file: UserFile): Boolean {
        if (!isSQLite3File(file)) return false
        val db = opener.open(file.pathString)
        var canHandle = true
        val count = db.querySingle(
            "select count(*) from SQLITE_MASTER where name='Habits' or name='Repetitions'"
        ) { it.getInt(0) }
        if (count == null || count != 2) {
            logger.error("Cannot handle file: tables not found")
            canHandle = false
        }
        if (db.getVersion() > DATABASE_VERSION) {
            logger.error("Cannot handle file: incompatible version: ${db.getVersion()} > $DATABASE_VERSION")
            canHandle = false
        }
        db.close()
        return canHandle
    }

    override suspend fun importHabitsFromFile(file: UserFile) {
        val db = opener.open(file.pathString)
        db.migrateTo(DATABASE_VERSION) { version ->
            val filename = org.isoron.platform.io.format("%02d.sql", version)
            fileOpener.openResourceFile("migrations/$filename").lines().joinToString("\n")
        }

        val habitDataList = loadHabits(db)
        for (habitData in habitDataList) {
            var habit = habitList.getByUUID(habitData.uuid)

            if (habit == null) {
                habit = modelFactory.buildHabit()
                val imported = habitData.copy(id = null)
                SQLiteHabitList.copyTo(imported, habit)
                CreateHabitCommand(modelFactory, habitList, habit).run()
            } else {
                val modified = modelFactory.buildHabit()
                SQLiteHabitList.copyTo(habitData.copy(id = habit.id), modified)
                EditHabitCommand(habitList, habit.id!!, modified).run()
            }

            habit = habitList.getByUUID(habitData.uuid)!!
            val entries = habit.originalEntries

            db.query(
                "SELECT timestamp, value, notes FROM Repetitions WHERE habit = ? ORDER BY timestamp DESC",
                habitData.id.toString()
            ) { stmt ->
                val timestamp = stmt.getLongOrNull(0) ?: return@query
                val value = stmt.getIntOrNull(1) ?: return@query
                val notes = stmt.getTextOrNull(2) ?: ""
                val date = LocalDate.fromUnixTime(timestamp)
                val (_, existingValue, existingNotes) = entries.get(date)
                if (existingValue != value || existingNotes != notes) {
                    entries.add(Entry(date, value, notes))
                }
            }
            importPauses(db, habitData.id, habit)
            habit.recompute()
        }
        habitList.resort()
        importOwnerIdentity(db)
        db.close()
    }

    /**
     * Adds the pauses of the backup to [habit]. Pauses that overlap days already paused
     * are skipped, so importing the same backup twice does not duplicate anything. Backups
     * from versions without pauses simply have none.
     */
    private fun importPauses(db: Database, backupHabitId: Long?, habit: Habit) {
        if (backupHabitId == null) return
        db.query(
            "SELECT startTimestamp, endTimestamp, title FROM HabitPauses WHERE habit = ? " +
                "ORDER BY startTimestamp",
            backupHabitId.toString()
        ) { stmt ->
            val start = stmt.getLongOrNull(0) ?: return@query
            val end = stmt.getLongOrNull(1) ?: return@query
            val title = stmt.getTextOrNull(2) ?: ""
            if (end < start) return@query
            val pause = Pause(LocalDate.fromUnixTime(start), LocalDate.fromUnixTime(end), title)
            if (!habit.pauses.overlaps(pause.start, pause.end)) habit.pauses.add(pause)
        }
    }

    /**
     * Takes over the owner identity of the backup, so that restoring a backup
     * on a new device keeps the same owner. Backups without an identity (older
     * versions) or with a malformed one leave the local identity unchanged.
     *
     * While this device has active shares under another identity, that
     * identity is kept: replacing it would orphan the shares on their servers.
     */
    private fun importOwnerIdentity(db: Database) {
        val imported = OwnerIdentityStore(db).load()
        if (imported == null) {
            logger.info("Backup has no valid owner identity, keeping the local one")
            return
        }
        val local = ownerIdentityStore.load()
        if (local != null && local.ownerId != imported.ownerId && shareStore.activeServers().isNotEmpty()) {
            logger.info("Keeping the local owner identity: this device has active shares")
            return
        }
        ownerIdentityStore.save(imported)
        logger.info("Imported owner identity ${imported.ownerId}")
    }

    private fun loadHabits(db: Database): List<HabitData> {
        val result = mutableListOf<HabitData>()
        db.query(
            "SELECT id, name, description, freq_num, freq_den, color, " +
                "position, reminder_hour, reminder_min, reminder_days, highlight, " +
                "archived, type, target_value, target_type, unit, uuid, category, step_size, negative_since " +
                "FROM Habits ORDER BY position"
        ) { stmt ->
            result.add(
                HabitData(
                    id = stmt.getLongOrNull(0),
                    name = stmt.getTextOrNull(1) ?: "",
                    description = stmt.getTextOrNull(2) ?: "",
                    freqNum = stmt.getIntOrNull(3) ?: 1,
                    freqDen = stmt.getIntOrNull(4) ?: 1,
                    color = stmt.getIntOrNull(5) ?: 0,
                    position = stmt.getIntOrNull(6) ?: 0,
                    reminderHour = stmt.getIntOrNull(7),
                    reminderMin = stmt.getIntOrNull(8),
                    reminderDays = stmt.getIntOrNull(9) ?: 0,
                    highlight = stmt.getIntOrNull(10) ?: 0,
                    archived = stmt.getIntOrNull(11) ?: 0,
                    type = stmt.getIntOrNull(12) ?: 0,
                    targetValue = stmt.getRealOrNull(13) ?: 0.0,
                    targetType = stmt.getIntOrNull(14) ?: 0,
                    unit = stmt.getTextOrNull(15) ?: "",
                    uuid = stmt.getTextOrNull(16),
                    category = stmt.getText(17),
                    stepSize = stmt.getRealOrNull(18) ?: 1.0,
                    negativeSince = stmt.getLongOrNull(19)
                )
            )
        }
        return result
    }
}
