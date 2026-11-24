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
package org.isoron.uhabits.core.commands

import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList

data class CreateRepetitionCommand(
    val habitList: HabitList,
    val habit: Habit,
    val date: LocalDate,
    val value: Int,
    val notes: String,
    val source: EntrySource = EntrySource.APP
) : Command {
    override fun run() {
        val entries = habit.originalEntries
        var newValue = value
        if (value == Entry.NO && habit.pauses.isPaused(date)) {
            // On a paused day, "not done" is shown as a dash instead of a cross: tapping an
            // empty paused day checks it, and unchecking it returns it to the paused state.
            newValue = if (entries.get(date).value == Entry.UNKNOWN) Entry.YES_MANUAL else Entry.UNKNOWN
        }
        entries.add(Entry(date, newValue, notes))
        habit.recompute()
        habitList.resort()
    }
}
