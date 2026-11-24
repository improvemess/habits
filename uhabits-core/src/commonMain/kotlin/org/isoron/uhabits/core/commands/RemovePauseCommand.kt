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

import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.Pause

/**
 * Ends a pause. The past is never changed: days up to and including today remain paused, and
 * only the days after today are released. A pause that lies entirely in the past is kept as is.
 */
data class RemovePauseCommand(
    val habitList: HabitList,
    val habit: Habit,
    val pause: Pause
) : Command {
    override fun run() {
        val today = getToday()
        if (pause.end.isOlderThan(today)) return
        habit.pauses.remove(pause)
        if (!pause.start.isNewerThan(today)) {
            habit.pauses.add(Pause(pause.start, today))
        }
        habit.recompute()
        habitList.resort()
    }
}
