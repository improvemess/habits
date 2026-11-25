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
package org.isoron.uhabits.core.ui.screens.habits.list

import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.HabitList.Order
import org.isoron.uhabits.core.models.HabitMatcher
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.ui.views.Theme

data class ListHabitsState(
    val items: List<HabitCardState>,
    val hasHabits: Boolean,
    val primaryOrder: Order,
    val secondaryOrder: Order,
    val isSortable: Boolean,
    val checkmarkCount: Int,
    val theme: Theme,
    val showStreak: Boolean = false
)

object ListHabitsPresenter {
    fun buildState(
        allHabits: HabitList,
        filter: HabitMatcher?,
        primaryOrder: Order,
        secondaryOrder: Order,
        checkmarkCount: Int,
        theme: Theme,
        showStreak: Boolean = false,
        /** UUIDs of habits shared with friends; their cards get a marker. */
        sharedHabits: Set<String> = emptySet()
    ): ListHabitsState {
        val view = allHabits.getFiltered(filter)
        view.primaryOrder = primaryOrder
        view.secondaryOrder = secondaryOrder

        val items = mutableListOf<HabitCardState>()
        for (habit in view) {
            val id = habit.id ?: continue
            items.add(cardState(habit, id, checkmarkCount, isShared = habit.uuid in sharedHabits))
        }
        return ListHabitsState(
            items = items,
            hasHabits = !allHabits.isEmpty,
            primaryOrder = primaryOrder,
            secondaryOrder = secondaryOrder,
            isSortable = primaryOrder == Order.BY_POSITION,
            checkmarkCount = checkmarkCount,
            theme = theme,
            showStreak = showStreak
        )
    }

    /**
     * The card of one habit with its last [checkmarkCount] days. Also used for
     * friends' habits, which are shown with the same cards (read-only).
     */
    fun cardState(habit: Habit, id: Long, checkmarkCount: Int, isShared: Boolean = false): HabitCardState {
        val today = getToday()
        val dateFrom = today.minus(checkmarkCount - 1)
        val score = habit.scores[today].value
        val ongoing = habit.streaks.getOngoingStreak(today)
        val streak = ongoing?.let { countPerformedDays(habit, it.start, it.end) } ?: 0
        // A manually skipped day keeps the streak alive, but it is not "done". During a pause
        // the flame stays colored (if there is a streak); on the first day after the pause it
        // is gray again until the streak is continued or lapses.
        val skippedToday = habit.computedEntries.get(today).value == Entry.SKIP
        val streakDoneToday = ongoing != null && ongoing.end >= today &&
            (!skippedToday || (streak > 0 && habit.pauses.isPaused(today)))
        val entries = habit.computedEntries.getByInterval(dateFrom, today)
        return HabitCardState(
            id = id,
            name = habit.name,
            color = habit.color,
            unit = habit.unit,
            isNumerical = habit.isNumerical,
            isNegative = habit.isNegative,
            archived = habit.isArchived,
            score = score,
            checkmarks = entries.map { it.value },
            completed = entries.map { it.completed },
            notes = entries.map { it.notes },
            paused = if (habit.pauses.isEmpty()) {
                emptyList()
            } else {
                entries.map { it.value == Entry.SKIP && habit.pauses.isPaused(it.date) }
            },
            category = habit.category,
            streak = streak,
            streakDoneToday = streakDoneToday,
            isShared = isShared
        )
    }
}

data class HabitCardState(
    val id: Long,
    val name: String,
    val color: PaletteColor,
    val unit: String,
    val isNumerical: Boolean,
    val isNegative: Boolean = false,
    val archived: Boolean,
    val score: Double,
    val checkmarks: List<Int>,
    val completed: List<Boolean>,
    val notes: List<String>,
    val paused: List<Boolean> = emptyList(),
    val category: String,
    val streak: Int = 0,
    val streakDoneToday: Boolean = false,
    val isShared: Boolean = false
)

/**
 * Counts the days in [from]..[to] on which the habit was actually performed. Days that are only
 * covered because the frequency does not require a repetition (YES_AUTO) and skipped days keep
 * a streak alive, but do not increase its number.
 */
internal fun countPerformedDays(habit: Habit, from: LocalDate, to: LocalDate): Int {
    return habit.computedEntries.getByInterval(from, to).count {
        when {
            habit.isNumerical -> it.completed && it.value != Entry.SKIP
            else -> it.value == Entry.YES_MANUAL
        }
    }
}
