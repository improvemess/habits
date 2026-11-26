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

import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.TruncateField
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.countRelapses
import org.isoron.uhabits.core.models.groupedSum
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.ui.views.Theme
import kotlin.math.max
import kotlin.math.roundToInt

data class BarCardState(
    val theme: Theme,
    val boolSpinnerPosition: Int,
    val bucketSize: Int,
    val color: PaletteColor,
    val entries: List<Entry>,
    val isNumerical: Boolean,
    val numericalSpinnerPosition: Int,
    /** Per entry (same order): the top part of the bar that is drawn in red, if any. */
    val redParts: List<Double> = emptyList(),
    /** Per entry: numbers above the bar, in the bar color and in red. Empty means the value. */
    val labels: List<String> = emptyList(),
    val redLabels: List<String> = emptyList()
)

class BarCardPresenter(
    val preferences: Preferences,
    val screen: Screen
) {
    companion object {
        val numericalBucketSizes = intArrayOf(1, 7, 31, 92, 365)
        val boolBucketSizes = intArrayOf(7, 31, 92, 365)

        fun buildState(
            habit: Habit,
            firstWeekday: Int,
            numericalSpinnerPosition: Int,
            boolSpinnerPosition: Int,
            theme: Theme
        ): BarCardState {
            val bucketSize = if (habit.isNumerical) {
                numericalBucketSizes[numericalSpinnerPosition]
            } else {
                boolBucketSizes[boolSpinnerPosition]
            }
            val today = getToday()
            val oldest = habit.computedEntries.getKnown().lastOrNull()?.date ?: today
            val truncateField = ScoreCardPresenter.getTruncateField(bucketSize)
            val days = habit.computedEntries.getByInterval(oldest, today)
            var entries = days.groupedSum(
                truncateField = truncateField,
                firstWeekday = firstWeekday,
                isNumerical = habit.isNumerical
            )
            var redParts = emptyList<Double>()
            var labels = emptyList<String>()
            var redLabels = emptyList<String>()
            if (habit.isNegative && habit.isNumerical) {
                // The part of a bar above the limit is red
                redParts = entries.map {
                    max(0.0, it.value / 1000.0 - limitFor(habit, truncateField, it.date))
                }
            } else if (habit.isNegative) {
                // Days kept and relapses: the bar is the tracked days, the relapses are red
                val relapses = days.countRelapses(truncateField, firstWeekday).associate { it.date to it.value }
                val kept = entries.map { (it.value / 1000.0).roundToInt() }
                val relapsed = entries.map { relapses[it.date] ?: 0 }
                entries = entries.mapIndexed { i, e -> Entry(e.date, (kept[i] + relapsed[i]) * 1000) }
                redParts = relapsed.map { it.toDouble() }
                labels = kept.map { it.toString() }
                redLabels = relapsed.map { if (it > 0) it.toString() else "" }
            }
            return BarCardState(
                theme = theme,
                entries = entries,
                bucketSize = bucketSize,
                color = habit.color,
                isNumerical = habit.isNumerical,
                numericalSpinnerPosition = numericalSpinnerPosition,
                boolSpinnerPosition = boolSpinnerPosition,
                redParts = redParts,
                labels = labels,
                redLabels = redLabels
            )
        }

        /** The limit that applies to one bucket (day, week, month, quarter or year) starting at [date]. */
        private fun limitFor(habit: Habit, field: TruncateField, date: LocalDate): Double {
            val denominator = habit.valuePeriodDays
            val daily = habit.targetValue / denominator
            return when {
                field == TruncateField.DAY && denominator <= 1 -> habit.targetValue
                field == TruncateField.WEEK_NUMBER && denominator == 7 -> habit.targetValue
                field == TruncateField.MONTH && denominator == 30 -> habit.targetValue
                field == TruncateField.YEAR && denominator == Frequency.YEAR_DAYS -> habit.targetValue
                else -> daily * when (field) {
                    TruncateField.DAY -> 1
                    TruncateField.WEEK_NUMBER -> 7
                    TruncateField.MONTH -> date.monthLength
                    TruncateField.QUARTER -> 91
                    TruncateField.YEAR -> date.yearLength
                }
            }
        }
    }

    fun onNumericalSpinnerPosition(position: Int) {
        preferences.barCardNumericalSpinnerPosition = position
        screen.updateWidgets()
        screen.refresh()
    }

    fun onBoolSpinnerPosition(position: Int) {
        preferences.barCardBoolSpinnerPosition = position
        screen.updateWidgets()
        screen.refresh()
    }

    interface Screen {
        fun updateWidgets()
        fun refresh()
    }
}
