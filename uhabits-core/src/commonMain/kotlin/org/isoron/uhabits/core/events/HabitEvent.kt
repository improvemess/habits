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
import org.isoron.uhabits.core.commands.EntrySource

/**
 * Transport-agnostic description of a change to a habit entry.
 *
 * Contains domain values only; how they are serialized (key names, date
 * format, ...) is up to each [HabitEventTransport].
 */
data class HabitEvent(
    val habitUuid: String,
    val habitName: String,
    val category: String,
    /** Date of the changed entry. */
    val date: LocalDate,
    val state: EntryState,
    /**
     * Value of the entry in the habit's unit. 1.0 for "yes" / 0.0 for "no" on
     * yes/no habits; null if unknown or skipped.
     */
    val value: Double?,
    val habitType: Type,
    val unit: String,
    /** Target value (numerical habits only, null otherwise). */
    val target: Double?,
    /** Target type (numerical habits only, null otherwise). */
    val targetType: TargetType?,
    /** Whether the habit counts as completed on [date]. */
    val completed: Boolean,
    /** Length in days of the current streak, as of today. */
    val currentStreak: Int,
    /** Habit strength as of today, between 0.0 and 1.0. */
    val score: Double,
    val source: EntrySource
) {
    enum class EntryState {
        YES,
        YES_AUTO,
        NO,
        SKIP,
        UNKNOWN,

        /** A numerical habit with a known value. */
        VALUE
    }

    enum class Type {
        YES_NO,
        NUMERICAL
    }

    enum class TargetType {
        AT_LEAST,
        AT_MOST
    }
}
