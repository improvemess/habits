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

import org.isoron.platform.time.LocalDate

/**
 * A period during which a habit is paused (for example, during a vacation). Both [start] and
 * [end] are inclusive. The [title] is an optional reason, such as "Vacation".
 */
data class Pause(val start: LocalDate, val end: LocalDate, val title: String = "") {
    init {
        require(!end.isOlderThan(start)) { "end of pause must not be before its start" }
    }

    fun contains(date: LocalDate): Boolean = !date.isOlderThan(start) && !date.isNewerThan(end)
}
