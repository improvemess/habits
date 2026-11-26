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
package org.isoron.uhabits.core.ui.screens.habits.show.views

import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.models.CheatDayEvaluator
import org.isoron.uhabits.core.models.CheatDayHistory
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.StreakUnit
import org.isoron.uhabits.core.ui.views.Theme

/**
 * The cheat days of a habit (see #20), shown at the top of the habit screen. The card is hidden
 * while the global level has no cheat days (Strict).
 */
data class CheatDaysCardState(
    val visible: Boolean,
    val color: PaletteColor,
    /** Cheat days left. */
    val cheatDays: Int = 0,
    /** Most cheat days the current level can hold. */
    val cap: Int = 0,
    /** Cheat days a new streak starts with. */
    val startCheatDays: Int = 0,
    /** There is a running streak that cheat days protect. */
    val streakAlive: Boolean = false,
    /** The latest due unit was bridged by a cheat day. */
    val usedLast: Boolean = false,
    /** No cheat day left: the next miss breaks the streak. */
    val atRisk: Boolean = false,
    /** Done units until the next cheat day is earned; null if none can be earned now. */
    val untilNext: Int? = null,
    val unit: StreakUnit = StreakUnit.DAYS,
    val theme: Theme
)

class CheatDaysCardPresenter {
    companion object {
        fun buildState(
            habit: Habit,
            history: CheatDayHistory,
            firstWeekday: DayOfWeek,
            theme: Theme
        ): CheatDaysCardState {
            val today = getToday()
            val level = history.levelOn(today)
            if (level.cap == 0) return CheatDaysCardState(visible = false, color = habit.color, theme = theme)
            val result = CheatDayEvaluator.forHabit(habit, history, today, firstWeekday)
            return CheatDaysCardState(
                visible = true,
                color = habit.color,
                cheatDays = result.cheatDays,
                cap = level.cap,
                startCheatDays = level.startCheatDays,
                streakAlive = result.alive,
                usedLast = result.used,
                atRisk = result.atRisk,
                untilNext = result.untilNext,
                unit = habit.streakUnit ?: StreakUnit.defaultFor(habit.effectiveFrequency),
                theme = theme
            )
        }
    }
}
