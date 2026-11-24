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

/**
 * Pauses a habit for the period [from]..[to] (inclusive) by marking each day as SKIP, or, if
 * [paused] is false, resumes it by clearing the SKIP marks in that period.
 *
 * Days that already contain a real entry (a check mark or a numerical value) are left untouched.
 */
data class PauseHabitCommand(
    val habitList: HabitList,
    val habit: Habit,
    val from: LocalDate,
    val to: LocalDate,
    val paused: Boolean = true
) : Command {
    override fun run() {
        require(!to.isOlderThan(from)) { "end of pause must not be before its start" }
        val entries = habit.originalEntries
        var day = from
        while (!day.isNewerThan(to)) {
            val current = entries.get(day)
            if (paused) {
                if (current.value == Entry.UNKNOWN || current.value == Entry.NO) {
                    entries.add(Entry(day, Entry.SKIP, current.notes))
                }
            } else if (current.value == Entry.SKIP) {
                entries.add(Entry(day, Entry.UNKNOWN, current.notes))
            }
            day = day.plus(1)
        }
        habit.recompute()
        habitList.resort()
    }
}
