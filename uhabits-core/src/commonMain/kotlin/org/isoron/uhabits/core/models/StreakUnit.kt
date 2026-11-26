/*
 * Copyright (C) 2026 Christoph Schmidtmeier
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

package org.isoron.uhabits.core.models

import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.LocalDate

/** Unit in which the streak flame counts and shows a streak (see #15). */
enum class StreakUnit(val id: Int, val days: Int) {
    DAYS(0, 1),
    WEEKS(1, 7),
    MONTHS(2, 30),
    YEARS(3, 365);

    companion object {
        /** Stored value for "automatic", i.e. derived from the frequency. */
        const val AUTO_ID = -1

        fun fromId(id: Int): StreakUnit? = entries.firstOrNull { it.id == id }

        /** Default unit for a frequency: x per week -> weeks, per month -> months, per year -> years, else days. */
        fun defaultFor(frequency: Frequency): StreakUnit = when {
            frequency.denominator == 7 -> WEEKS
            frequency.denominator == 30 || frequency.denominator == 31 -> MONTHS
            frequency.isYearly -> YEARS
            else -> DAYS
        }

        /**
         * Converts a streak spanning [from]..[to] (both included) into [unit]. A streak of 30 days
         * is 4 weeks. In calendar mode only calendar weeks or months that lie completely inside the
         * streak count; otherwise the length of the streak is divided by 7 or 30 days.
         */
        fun convert(
            from: LocalDate,
            to: LocalDate,
            unit: StreakUnit,
            calendar: Boolean,
            firstWeekday: DayOfWeek
        ): Int {
            if (to < from) return 0
            return when {
                unit == DAYS -> from.daysUntil(to) + 1
                !calendar -> (from.daysUntil(to) + 1) / unit.days
                unit == WEEKS -> {
                    var start = from.startOfWeek(firstWeekday)
                    if (start < from) start = start.plus(7)
                    var count = 0
                    while (start.plus(6) <= to) {
                        count++
                        start = start.plus(7)
                    }
                    count
                }
                unit == YEARS -> {
                    var start = from.startOfYear()
                    if (start < from) start = LocalDate(start.year + 1, 1, 1)
                    var count = 0
                    while (LocalDate(start.year + 1, 1, 1).minus(1) <= to) {
                        count++
                        start = LocalDate(start.year + 1, 1, 1)
                    }
                    count
                }
                else -> {
                    var start = from.startOfMonth()
                    if (start < from) start = nextMonth(start)
                    var count = 0
                    while (nextMonth(start).minus(1) <= to) {
                        count++
                        start = nextMonth(start)
                    }
                    count
                }
            }
        }

        private fun nextMonth(monthStart: LocalDate): LocalDate = monthStart.plus(monthStart.monthLength)
    }
}
