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
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.Pause

/**
 * Moves the end of [pause] to [newEnd]. The new end cannot lie in the past, cannot reach into the
 * next pause, and cannot be further in the future than allowed for new pauses.
 */
data class ChangePauseEndCommand(
    val habitList: HabitList,
    val habit: Habit,
    val pause: Pause,
    val newEnd: LocalDate
) : Command {
    override fun run() {
        val range = habit.pauses.allowedEndRange(pause)
        require(newEnd >= range.start && newEnd <= range.endInclusive) { "new end of pause not allowed" }
        habit.pauses.remove(pause)
        habit.pauses.add(pause.copy(end = newEnd))
        habit.recompute()
        habitList.resort()
    }
}
