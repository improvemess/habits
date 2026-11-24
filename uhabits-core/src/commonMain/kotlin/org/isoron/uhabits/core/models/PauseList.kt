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

    fun isPaused(date: LocalDate): Boolean = getAll().any { it.contains(date) }
}
