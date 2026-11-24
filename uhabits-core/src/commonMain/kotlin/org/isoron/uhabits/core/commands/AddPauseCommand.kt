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
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.Pause
import org.isoron.uhabits.core.models.PauseList

/**
 * Pauses a habit from [from] to [to] (both inclusive), with an optional [title]. Pauses cannot
 * start in the past, cannot overlap existing pauses and cannot end more than
 * [PauseList.MAX_DAYS_AHEAD] days from now.
 */
data class AddPauseCommand(
    val habitList: HabitList,
    val habit: Habit,
    val from: LocalDate,
    val to: LocalDate,
    val title: String = ""
) : Command {
    override fun run() {
        require(!from.isOlderThan(getToday())) { "cannot pause a habit retroactively" }
        require(!to.isNewerThan(getToday().plus(PauseList.MAX_DAYS_AHEAD))) { "pause too far in the future" }
        require(!habit.pauses.overlaps(from, to)) { "pauses must not overlap" }
        habit.pauses.add(Pause(from, to, title))
        habit.recompute()
        habitList.resort()
    }
}
