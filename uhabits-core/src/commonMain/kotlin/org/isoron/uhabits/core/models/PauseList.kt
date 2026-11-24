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

import org.isoron.platform.Synchronized
import org.isoron.platform.time.LocalDate
import org.isoron.platform.time.getToday

/**
 * The list of pause periods of a single habit. This base implementation keeps everything in
 * memory; subclasses may persist the periods.
 */
open class PauseList {
    private val pauses = ArrayList<Pause>()

    @Synchronized
    open fun getAll(): List<Pause> = pauses.sortedBy { it.start.daysSince2000 }

    @Synchronized
    open fun add(pause: Pause) {
        if (pause !in pauses) pauses.add(pause)
    }

    @Synchronized
    open fun remove(pause: Pause) {
        pauses.remove(pause)
    }

    @Synchronized
    open fun clear() {
        pauses.clear()
    }

    fun isEmpty(): Boolean = getAll().isEmpty()

    /** The earliest and latest day to which [pause] can be extended or shortened. */
    fun allowedEndRange(pause: Pause, today: LocalDate = getToday()): ClosedRange<LocalDate> {
        val earliest = if (pause.start.isNewerThan(today)) pause.start else today
        var latest = today.plus(MAX_DAYS_AHEAD)
        val nextStart = getAll().filter { it != pause && it.start.isNewerThan(pause.start) }
            .minOfOrNull { it.start.daysSince2000 }
        if (nextStart != null && LocalDate(nextStart - 1).isOlderThan(latest)) latest = LocalDate(nextStart - 1)
        return earliest..latest
    }

    fun isPaused(date: LocalDate): Boolean = getAll().any { it.contains(date) }

    /** Returns true if any existing pause shares at least one day with [from]..[to]. */
    fun overlaps(from: LocalDate, to: LocalDate): Boolean =
        getAll().any { !it.start.isNewerThan(to) && !it.end.isOlderThan(from) }

    companion object {
        /** Pauses can only be planned this many days into the future. */
        const val MAX_DAYS_AHEAD = 31
    }
}
