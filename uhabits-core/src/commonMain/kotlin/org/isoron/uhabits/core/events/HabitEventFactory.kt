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
package org.isoron.uhabits.core.events

import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.commands.EntrySource
import org.isoron.uhabits.core.events.HabitEvent.EntryState
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.NumericalHabitType

/**
 * Builds [HabitEvent]s from the current (already recomputed) state of a habit.
 */
object HabitEventFactory {

    fun entryChanged(habit: Habit, date: LocalDate, source: EntrySource): HabitEvent {
        val raw = habit.computedEntries.get(date).value
        val today = getToday()
        return HabitEvent(
            habitUuid = habit.uuid!!,
            habitName = habit.name,
            category = habit.category,
            date = date.toCSVString(),
            state = stateOf(habit, raw),
            value = valueOf(habit, raw),
            habitType = if (habit.isNumerical) HabitEvent.Type.NUMERICAL else HabitEvent.Type.YES_NO,
            unit = habit.unit,
            target = if (habit.isNumerical) habit.targetValue else null,
            targetType = if (habit.isNumerical) targetTypeOf(habit.targetType) else null,
            completed = habit.isCompletedOn(date),
            currentStreak = currentStreak(habit, today),
            score = habit.scores[today].value,
            source = source
        )
    }

    private fun stateOf(habit: Habit, raw: Int): EntryState = when {
        raw == Entry.UNKNOWN -> EntryState.UNKNOWN
        raw == Entry.SKIP -> EntryState.SKIP
        habit.isNumerical -> EntryState.VALUE
        raw == Entry.YES_MANUAL -> EntryState.YES
        raw == Entry.YES_AUTO -> EntryState.YES_AUTO
        else -> EntryState.NO
    }

    private fun valueOf(habit: Habit, raw: Int): Double? = when {
        raw == Entry.UNKNOWN || raw == Entry.SKIP -> null
        habit.isNumerical -> raw / 1000.0
        raw == Entry.YES_MANUAL -> 1.0
        else -> 0.0
    }

    private fun targetTypeOf(type: NumericalHabitType) = when (type) {
        NumericalHabitType.AT_LEAST -> HabitEvent.TargetType.AT_LEAST
        NumericalHabitType.AT_MOST -> HabitEvent.TargetType.AT_MOST
    }

    /**
     * The streak that ends today, or yesterday (today may not be entered yet).
     * Zero if there is no such streak.
     */
    private fun currentStreak(habit: Habit, today: LocalDate): Int {
        val latest = habit.streaks.getBest(Int.MAX_VALUE).maxByOrNull { it.end } ?: return 0
        return if (latest.end == today || latest.end == today.minus(1)) latest.length else 0
    }
}
