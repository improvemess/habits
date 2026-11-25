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

/**
 * Transport-agnostic description of a change to a habit itself (as opposed to
 * one of its entries, see [HabitEvent]).
 */
sealed class HabitLifecycleEvent {
    abstract val habitUuid: String

    /** Something a [HabitSnapshot] contains has changed. */
    data class Updated(val snapshot: HabitSnapshot) : HabitLifecycleEvent() {
        override val habitUuid: String get() = snapshot.habitUuid
    }

    data class Archived(override val habitUuid: String) : HabitLifecycleEvent()

    data class Unarchived(override val habitUuid: String) : HabitLifecycleEvent()

    /** The habit and all its entries are gone. */
    data class Deleted(override val habitUuid: String) : HabitLifecycleEvent()
}

/**
 * The properties of a habit that describe it to someone else, e.g. a friend it
 * is shared with. Private details such as reminders, the archived state (see
 * [HabitLifecycleEvent.Archived]) and the position in the list are left out.
 */
data class HabitSnapshot(
    val habitUuid: String,
    val name: String,
    val description: String,
    val category: String,
    /** Index into the app's color palette. */
    val color: Int,
    val frequencyNumerator: Int,
    val frequencyDenominator: Int,
    val habitType: HabitEvent.Type,
    val unit: String,
    /** Target value (numerical habits only, null otherwise). */
    val target: Double?,
    /** Target type (numerical habits only, null otherwise). */
    val targetType: HabitEvent.TargetType?,
    /** First day an avoided ("negative") habit counts as done; null for normal habits. */
    val negativeSince: LocalDate? = null,
    /** Weekly goals count per calendar week instead of per rolling 7 days. */
    val calendarPeriod: Boolean = false
)
