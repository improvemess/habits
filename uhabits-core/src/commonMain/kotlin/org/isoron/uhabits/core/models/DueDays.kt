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
package org.isoron.uhabits.core.models

import org.isoron.platform.time.DayOfWeek
import org.isoron.platform.time.LocalDate

/** From [since] on (inclusive), the habit is due on the weekdays in [weekdays]; 0 means "not fixed". */
data class DueDaysChange(val since: LocalDate, val weekdays: Int)

/**
 * The fixed weekdays on which a habit is due (for example Monday, Wednesday and Friday), see #19.
 * The selection is a history: a change only applies from its day on, so that past days are
 * always judged by the weekdays that were due back then. Days before the first change, and days
 * after a change to "not fixed" (mask 0), are due every day.
 *
 * A weekday is the bit `1 shl DayOfWeek.daysSinceSunday` (Sunday = 1, Monday = 2, Saturday = 64).
 */
data class DueDays(val changes: List<DueDaysChange> = emptyList()) {
    /** The change list, oldest first, with one change per day at most. */
    private val sorted: List<DueDaysChange> =
        changes.sortedBy { it.since.daysSince2000 }.associateBy { it.since }.values.toList()

    /** The weekdays due on [date], or null if the habit is not fixed to weekdays on that day. */
    fun weekdaysOn(date: LocalDate): Int? {
        val change = sorted.lastOrNull { !it.since.isNewerThan(date) } ?: return null
        return change.weekdays.takeIf { it != 0 }
    }

    /** The weekdays that are due now (the latest change), or null if not fixed. */
    val current: Int?
        get() = sorted.lastOrNull()?.weekdays?.takeIf { it != 0 }

    /** Whether the habit is or ever was fixed to weekdays. */
    val isEverFixed: Boolean
        get() = sorted.any { it.weekdays != 0 }

    /** The first day from which the habit was fixed to weekdays. */
    val firstFixedSince: LocalDate?
        get() = sorted.firstOrNull { it.weekdays != 0 }?.since

    fun isDueOn(date: LocalDate): Boolean {
        val mask = weekdaysOn(date) ?: return true
        return mask and bit(date.dayOfWeek) != 0
    }

    /**
     * Returns the history with [weekdays] (0 or null for "not fixed") valid from [since] on.
     * Changes that were planned for later than [since] are replaced; nothing earlier is touched.
     */
    fun changedFrom(since: LocalDate, weekdays: Int?): DueDays {
        val mask = weekdays ?: 0
        val kept = sorted.filter { it.since.isOlderThan(since) }
        val before = kept.lastOrNull()?.weekdays ?: 0
        if (before == mask) return DueDays(kept)
        return DueDays(kept + DueDaysChange(since, mask))
    }

    /** Text stored in the database: `daysSince2000:mask` pairs separated by commas. */
    fun encode(): String = sorted.joinToString(",") { "${it.since.daysSince2000}:${it.weekdays}" }

    companion object {
        fun bit(day: DayOfWeek): Int = 1 shl day.daysSinceSunday

        const val ALL_DAYS = 127

        /** The mask as a [WeekdayList], whose index 0 is Saturday (as used for reminders). */
        fun toWeekdayList(mask: Int): WeekdayList =
            WeekdayList(BooleanArray(7) { i -> mask and (1 shl ((i + 6) % 7)) != 0 })

        fun fromWeekdayList(days: WeekdayList): Int {
            val array = days.toArray()
            var mask = 0
            for (i in 0..6) if (array[i]) mask = mask or (1 shl ((i + 6) % 7))
            return mask
        }

        fun decode(text: String?): DueDays {
            if (text.isNullOrBlank()) return DueDays()
            val result = mutableListOf<DueDaysChange>()
            for (part in text.split(",")) {
                val pair = part.split(":")
                val days = pair.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
                val mask = pair.getOrNull(1)?.trim()?.toIntOrNull() ?: continue
                result.add(DueDaysChange(LocalDate(days), mask and ALL_DAYS))
            }
            return DueDays(result)
        }
    }
}
