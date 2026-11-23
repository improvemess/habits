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
package org.isoron.uhabits.homeassistant

import org.isoron.uhabits.core.events.HabitEvent

/**
 * Wire format of [HabitEvent]s sent through the Home Assistant Companion App.
 *
 * The Companion App forwards every intent extra as a flat string into the
 * `event.data` of the HA event `android.intent_received`, so the payload is a
 * flat map of strings: automations can filter on it with plain `event_data`,
 * without templates. Nulls are left out. Numbers use `.` as the decimal
 * separator, regardless of locale.
 */
object CompanionAppPayload {
    const val ACTION_HABIT_EVENT = "org.isoron.uhabits.HABIT_EVENT"

    /** Version of this format; bump on incompatible changes. */
    const val VERSION = "1"

    const val TYPE_ENTRY_CHANGED = "entry_changed"

    fun extras(event: HabitEvent): Map<String, String> = buildMap {
        put("v", VERSION)
        put("type", TYPE_ENTRY_CHANGED)
        put("habit_uuid", event.habitUuid)
        put("habit_name", event.habitName)
        put("category", event.category)
        put("habit_type", event.habitType.wire())
        put("date", event.date.toCSVString())
        put("state", event.state.wire())
        event.value?.let { put("value", number(it)) }
        put("unit", event.unit)
        event.target?.let { put("target", number(it)) }
        event.targetType?.let { put("target_type", it.wire()) }
        put("completed", event.completed.toString())
        put("streak", event.currentStreak.toString())
        put("score", number(event.score))
        put("source", event.source.wire())
    }

    private fun Enum<*>.wire() = name.lowercase()

    /** `2.0` becomes `2`; everything else uses Kotlin's locale-free `toString()`. */
    internal fun number(value: Double): String {
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) asLong.toString() else value.toString()
    }
}
