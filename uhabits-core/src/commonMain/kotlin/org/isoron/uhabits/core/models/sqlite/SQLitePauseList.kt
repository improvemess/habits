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
package org.isoron.uhabits.core.models.sqlite

import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.database.PauseRepository
import org.isoron.uhabits.core.models.Pause
import org.isoron.uhabits.core.models.PauseList

class SQLitePauseList(private val repository: PauseRepository) : PauseList() {
    var habitId: Long? = null
    private var isLoaded = false

    private fun load() {
        if (isLoaded) return
        val habitId = habitId ?: throw IllegalStateException("habitId must be set")
        for ((start, end) in repository.findAllByHabitId(habitId)) {
            super.add(Pause(LocalDate.fromUnixTime(start), LocalDate.fromUnixTime(end)))
        }
        isLoaded = true
    }

    override fun getAll(): List<Pause> {
        load()
        return super.getAll()
    }

    override fun add(pause: Pause) {
        load()
        if (pause in super.getAll()) return
        repository.insert(habitId!!, pause.start.unixTime, pause.end.unixTime)
        super.add(pause)
    }

    override fun remove(pause: Pause) {
        load()
        repository.delete(habitId!!, pause.start.unixTime, pause.end.unixTime)
        super.remove(pause)
    }

    override fun clear() {
        load()
        repository.deleteByHabitId(habitId!!)
        super.clear()
    }
}
